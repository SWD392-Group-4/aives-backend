const path = require('path');
const { VertexAI } = require('@google-cloud/vertexai');

// 1. Cấu hình định danh từ Bước 1
const project = 'precise-ray-512018-v9';
const location = 'asia-southeast1';
const corpusId = '6917529027641081856';

// Đường dẫn tài nguyên RAG Corpus đầy đủ
const ragCorpusResource = `projects/${project}/locations/${location}/ragCorpora/${corpusId}`;

// 2. Khởi tạo Vertex AI Client (sử dụng file rag-key.json)
const keyFile = path.resolve(__dirname, '../rag-key.json');
process.env.GOOGLE_APPLICATION_CREDENTIALS = keyFile;
console.log(`[Setup] Sử dụng credentials từ: ${keyFile}`);
console.log(`[Setup] Target RAG Corpus: ${ragCorpusResource}`);

const vertexAI = new VertexAI({
  project,
  location,
  googleAuthOptions: {
    keyFile: keyFile
  }
});

async function queryExamRAG(studentQuestion) {
  console.log(`\n[Query] Đang gửi câu hỏi: "${studentQuestion}"...`);

  const modelName = process.env.MODEL_NAME || 'gemini-2.5-flash';
  console.log(`[Model] Sử dụng model: ${modelName}`);

  // 3. Khởi tạo model Gemini tích hợp công cụ RAG Engine
  const generativeModel = vertexAI.getGenerativeModel({
    model: modelName,
    generationConfig: {
      temperature: 0.2, // Nhiệt độ thấp giúp câu trả lời bám sát slide
    },
    tools: [
      {
        retrieval: {
          vertexRagStore: {
            ragResources: [
              {
                ragCorpus: ragCorpusResource,
              },
            ],
            similarityTopK: 5, // Lấy 5 đoạn văn bản liên quan nhất từ slide
          },
        },
      },
    ],
  });

  const prompt = `Bạn là trợ lý khảo thí. Dựa vào tài liệu bài giảng được cung cấp, hãy trả lời câu hỏi sau của sinh viên: "${studentQuestion}"`;

  try {
    const response = await generativeModel.generateContent(prompt);
    const candidate = response.response.candidates?.[0];
    const resultText = candidate?.content?.parts?.[0]?.text;
    console.log('\n--- Phản hồi từ bài giảng ---');
    console.log(resultText);
    console.log('------------------------------\n');
    return resultText;
  } catch (error) {
    console.error('Lỗi khi truy vấn RAG:', error);
  }
}

// Chạy thử với câu hỏi liên quan đến Ch01_Introduction.pptx
queryExamRAG('Mục tiêu chính của chương 1 là gì?');
