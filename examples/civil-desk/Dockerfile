# civil-desk (TypeScript/Node/Fastify) — 멀티스테이지 · 비루트 실행
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json tsconfig.json ./
RUN npm install
COPY src ./src
RUN npm run build

FROM node:20-alpine
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev && addgroup -S app && adduser -S -u 1001 -G app app
COPY --from=build /app/dist ./dist
USER app
ENV PORT=8080
EXPOSE 8080
CMD ["node", "dist/server.js"]
