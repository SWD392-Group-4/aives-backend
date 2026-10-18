const path = require('path');
const { VertexAI } = require('@google-cloud/vertexai');

// 1. Cấu hình định danh
const project = 'precise-ray-512018-v9';
const location = 'asia-southeast1';
const corpusId = '6917529027641081856';

// Đường dẫn tài nguyên RAG Corpus đầy đủ
const ragCorpusResource = `projects/${project}/locations/${location}/ragCorpora/${corpusId}`;

// 2. Khởi tạo Vertex AI Client (sử dụng file key json đã tải về)
const keyFile = path.resolve(__dirname, '../rag-key.json');
process.env.GOOGLE_APPLICATION_CREDENTIALS = keyFile;

const vertexAI = new VertexAI({
  project,
  location,
  googleAuthOptions: {
    keyFile: keyFile
  }
});

async function queryExamRAG(studentQuestion) {
  // 3. Khởi tạo model Gemini tích hợp công cụ RAG Engine (asia-southeast1 hỗ trợ gemini-2.5-flash)
  const generativeModel = vertexAI.getGenerativeModel({
    model: 'gemini-2.5-flash',
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
const resultText = response.response.candidates[0].content.parts[0].text;
console.log('Phản hồi từ bài giảng:', resultText);
return resultText;
} catch (error) {
console.error('Lỗi khi truy vấn RAG:', error);
}
}

// Chạy thử với câu hỏi liên quan đến Ch01_Introduction.pptx
queryExamRAG('Mục tiêu chính của chương 1 là gì?');
