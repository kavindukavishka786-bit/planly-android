import { createServer } from 'node:http';
import { createHandler, configurationProblem } from './app.mjs';

const config = {
  apiKey: process.env.GEMINI_API_KEY, token: process.env.APP_ACCESS_TOKEN,
  model: process.env.GEMINI_MODEL || 'gemini-3.5-flash-lite'
};
const problem = configurationProblem(config);
if (problem) console.warn(`PLANLY AI setup required: ${problem}`);
const server = createServer(createHandler(config));
server.requestTimeout = 60_000;
server.headersTimeout = 15_000;
server.listen(Number(process.env.PORT || 8080), '0.0.0.0', () => console.log('PLANLY AI service is ready'));
