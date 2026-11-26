# o build do Angular roda uma vez so, na arquitetura de quem gera a imagem ($BUILDPLATFORM):
# o resultado (HTML/JS/CSS) e igual pra qualquer servidor, so o Nginx muda (amd64 ou arm64)
FROM --platform=$BUILDPLATFORM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . ./
RUN npm run build -- --configuration production

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
