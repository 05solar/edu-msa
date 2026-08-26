FROM node:20-alpine

WORKDIR /app
COPY package.json ./
RUN npm install --no-audit --no-fund

COPY src ./src
# 비루트 실행 (node 사용자 uid 1000) + tsx 캐시 쓰기 가능하도록 소유권 부여
RUN chown -R node:node /app
USER node

ENV PORT=8080
EXPOSE 8080
CMD ["npm", "start"]
