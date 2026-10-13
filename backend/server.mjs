import { createServer } from 'node:http';
import { createHandler } from './app.mjs';

const server = createServer(createHandler({
  apiKey: process.env.GEMINI_API_KEY, token: process.env.APP_ACCESS_TOKEN,
  model: process.env.GEMINI_MODEL || 'gemini-3.5-flash-lite'
}));
server.requestTimeout = 60_000;
server.headersTimeout = 15_000;
server.listen(Number(process.env.PORT || 8080), '0.0.0.0', () => console.log('PLANLY AI service is ready'));
