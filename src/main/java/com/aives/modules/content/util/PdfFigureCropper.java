package com.aives.modules.content.util;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;

/**
 * Cat hinh ve cua tung cau hoi tu file PDF.
 *
 * <p>AI (Gemini) chi bao hinh nam o trang nao va khung gan dung [ymin, xmin, ymax, xmax] theo thang 0 - 1000.
 * Khung cua AI thuong lech vai phan tram nen:
 * <ol>
 *   <li>Neu trang co anh nhung (anh PNG / JPG dan vao Word roi xuat PDF) trung voi khung cua AI thi cat dung
 *       theo vi tri that cua anh do (chinh xac tung diem anh).</li>
 *   <li>Neu khong (hinh ve bang cong cu ve cua Word, PDF scan...) thi cat theo khung cua AI, noi them 1 chut.</li>
 * </ol>
 * Luon cat tren anh render cua ca trang, nen chu / ky hieu ve de len anh (vd so do goc) van giu nguyen.
 */
public final class PdfFigureCropper {

    /** Do phan giai khi ve trang PDF thanh anh. */
    static final float RENDER_DPI = 160f;
    /** Anh hinh ve toi da rong / cao bao nhieu diem anh. */
    static final int MAX_SIDE_PX = 1400;
    /** Thang toa do cua AI. */
    static final float SCALE = 1000f;

    private static final float MIN_IOU = 0.3f;
    // Noi them quanh khung (don vi 0 - 1000 cua trang): khung lay theo anh nhung that can it, khung cua AI can nhieu hon
    private static final float IMAGE_PADDING = 6f;
    private static final float AI_BOX_PADDING = 12f;
    // Anh nhung nho hon muc nay (logo, icon) hoac gan bang ca trang (trang scan) thi khong dung de can khung
    private static final float MIN_IMAGE_SIDE = 25f;
    private static final float MAX_IMAGE_AREA_RATIO = 0.8f;

    private PdfFigureCropper() {
    }

    /**
     * Yeu cau cat 1 hinh.
     *
     * @param page so trang, bat dau tu 1
     * @param box  [ymin, xmin, ymax, xmax] thang 0 - 1000 (goc tren ben trai la 0,0); null hoac sai thi tu chon anh nhung
     *             tren trang do
     */
    public record FigureRequest(int page, int[] box) {
    }

    /** Anh PNG da cat. */
    public record CroppedFigure(byte[] png, int width, int height, int page) {
    }

    /** Khung chu nhat theo thang 0 - 1000, goc tren ben trai la (0, 0). */
    record Box(float top, float left, float bottom, float right) {

        float width() {
            return right - left;
        }

        float height() {
            return bottom - top;
        }

        float area() {
            return Math.max(0, width()) * Math.max(0, height());
        }

        Box expand(float padding) {
            return new Box(clamp(top - padding), clamp(left - padding), clamp(bottom + padding), clamp(right + padding));
        }

        float iou(Box other) {
            float interW = Math.min(right, other.right) - Math.max(left, other.left);
            float interH = Math.min(bottom, other.bottom) - Math.max(top, other.top);
            if (interW <= 0 || interH <= 0) {
                return 0;
            }
            float inter = interW * interH;
            return inter / (area() + other.area() - inter);
        }

        private static float clamp(float value) {
            return Math.max(0, Math.min(SCALE, value));
        }
    }

    /**
     * Cat cac hinh theo danh sach yeu cau. Phan tu thu i cua ket qua ung voi yeu cau thu i; null neu khong cat duoc
     * (trang khong ton tai, khung sai...). Loi doc ca file thi nem IOException.
     */
    public static List<CroppedFigure> crop(byte[] pdfBytes, List<FigureRequest> requests) throws IOException {
        List<CroppedFigure> result = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            Map<Integer, BufferedImage> renderedPages = new HashMap<>();
            Map<Integer, List<Box>> pageImages = new HashMap<>();
            // Anh nhung da dung cho cau truoc: cau sau (khong co khung) se lay anh ke tiep tren trang
            Map<Integer, Set<Box>> usedImages = new HashMap<>();

            for (FigureRequest request : requests) {
                int pageIndex = request == null ? -1 : request.page() - 1;
                if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                    result.add(null);
                    continue;
                }
                PDPage page = document.getPage(pageIndex);
                List<Box> images = pageImages.computeIfAbsent(pageIndex, index -> locateImages(page));
                Set<Box> used = usedImages.computeIfAbsent(pageIndex, index -> new HashSet<>());

                Box region = chooseRegion(toBox(request.box()), images, used);
                if (region == null) {
                    result.add(null);
                    continue;
                }
                BufferedImage pageImage = renderedPages.get(pageIndex);
                if (pageImage == null) {
                    pageImage = renderer.renderImageWithDPI(pageIndex, RENDER_DPI, ImageType.RGB);
                    renderedPages.put(pageIndex, pageImage);
                }
                result.add(cut(pageImage, region, request.page()));
            }
        }
        return result;
    }

    /* ========================= CHON KHUNG ========================= */

    static Box chooseRegion(Box aiBox, List<Box> images, Set<Box> used) {
        if (aiBox != null) {
            Box best = null;
            float bestScore = 0;
            for (Box image : images) {
                float score = image.iou(aiBox);
                if (score > bestScore) {
                    bestScore = score;
                    best = image;
                }
            }
            if (best != null && bestScore >= MIN_IOU) {
                used.add(best);
                return best.expand(IMAGE_PADDING);
            }
            return aiBox.expand(AI_BOX_PADDING);
        }
        // AI biet trang nhung khong dua khung hop le: lay anh nhung chua dung dau tien (tu tren xuong)
        for (Box image : images) {
            if (!used.contains(image)) {
                used.add(image);
                return image.expand(IMAGE_PADDING);
            }
        }
        return null;
    }

    /** [ymin, xmin, ymax, xmax] cua AI -> Box; null neu thieu / sai / qua nho. */
    static Box toBox(int[] box) {
        if (box == null || box.length != 4) {
            return null;
        }
        Box result = new Box(box[0], box[1], box[2], box[3]);
        boolean valid = result.top() >= 0 && result.left() >= 0 && result.bottom() <= SCALE && result.right() <= SCALE
                && result.width() >= 20 && result.height() >= 20;
        return valid ? result : null;
    }

    private static CroppedFigure cut(BufferedImage pageImage, Box region, int pageNo) throws IOException {
        int x = Math.round(region.left() / SCALE * pageImage.getWidth());
        int y = Math.round(region.top() / SCALE * pageImage.getHeight());
        int w = Math.min(pageImage.getWidth() - x, Math.round(region.width() / SCALE * pageImage.getWidth()));
        int h = Math.min(pageImage.getHeight() - y, Math.round(region.height() / SCALE * pageImage.getHeight()));
        if (w < 10 || h < 10) {
            return null;
        }
        BufferedImage figure = pageImage.getSubimage(x, y, w, h);
        double shrink = Math.min(1.0, (double) MAX_SIDE_PX / Math.max(w, h));
        if (shrink < 1.0) {
            int newW = (int) Math.round(w * shrink);
            int newH = (int) Math.round(h * shrink);
            BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            var graphics = scaled.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(figure, 0, 0, newW, newH, null);
            graphics.dispose();
            figure = scaled;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(figure, "png", out);
        return new CroppedFigure(out.toByteArray(), figure.getWidth(), figure.getHeight(), pageNo);
    }

    /* ========================= TIM ANH NHUNG TRONG TRANG ========================= */

    /** Vi tri cac anh nhung tren trang (thang 0 - 1000, sap tu tren xuong). Trang xoay thi bo qua (dung khung cua AI). */
    static List<Box> locateImages(PDPage page) {
        if (page.getRotation() % 360 != 0) {
            return List.of();
        }
        PDRectangle cropBox = page.getCropBox();
        ImageLocator locator = new ImageLocator(cropBox);
        try {
            locator.processPage(page);
        } catch (IOException | RuntimeException ex) {
            // Trang loi cau truc: van cat duoc theo khung cua AI
            return List.of();
        }
        List<Box> images = new ArrayList<>(locator.images);
        images.sort(Comparator.comparing(Box::top).thenComparing(Box::left));
        return images;
    }

    private static final class ImageLocator extends PDFStreamEngine {

        private final PDRectangle cropBox;
        private final List<Box> images = new ArrayList<>();

        ImageLocator(PDRectangle cropBox) {
            this.cropBox = cropBox;
            addOperator(new Concatenate());
            addOperator(new DrawObject());
            addOperator(new SetGraphicsStateParameters());
            addOperator(new Save());
            addOperator(new Restore());
            addOperator(new SetMatrix());
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if ("Do".equals(operator.getName()) && !operands.isEmpty() && operands.get(0) instanceof COSName name) {
                PDXObject xObject = getResources().getXObject(name);
                if (xObject instanceof PDImageXObject) {
                    addImage(getGraphicsState().getCurrentTransformationMatrix());
                    return;
                }
                if (xObject instanceof PDFormXObject form) {
                    showForm(form);
                    return;
                }
            }
            super.processOperator(operator, operands);
        }

        /** Anh luon ve vao hinh vuong don vi (0,0)-(1,1) bien doi qua ma tran hien tai. */
        private void addImage(Matrix ctm) {
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            for (float[] corner : new float[][] {{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
                Point2D.Float point = ctm.transformPoint(corner[0], corner[1]);
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
            }
            float pageW = cropBox.getWidth();
            float pageH = cropBox.getHeight();
            // PDF: goc toa do o duoi ben trai, truc y huong len -> doi sang goc tren ben trai
            Box box = new Box(
                    (cropBox.getUpperRightY() - maxY) / pageH * SCALE,
                    (minX - cropBox.getLowerLeftX()) / pageW * SCALE,
                    (cropBox.getUpperRightY() - minY) / pageH * SCALE,
                    (maxX - cropBox.getLowerLeftX()) / pageW * SCALE);
            boolean tooSmall = box.width() < MIN_IMAGE_SIDE || box.height() < MIN_IMAGE_SIDE;
            boolean wholePage = box.area() > MAX_IMAGE_AREA_RATIO * SCALE * SCALE;
            if (!tooSmall && !wholePage) {
                images.add(box.expand(0));
            }
        }
    }
}
