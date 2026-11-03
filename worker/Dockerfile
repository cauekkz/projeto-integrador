# 1) compila: imagem com o Go, so pra gerar o binario
FROM golang:1.25-alpine AS build
WORKDIR /src
# dependencias primeiro: se so o codigo mudar, o Docker reaproveita essa camada
COPY go.mod go.sum ./
RUN go mod download
COPY . .
# binario estatico (sem libc), sem simbolos de debug
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /worker ./cmd

# 2) roda: imagem minima so com o binario (sem Go, sem codigo-fonte, sem shell). O fuso de Sao Paulo vai dentro do binario
FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /worker /worker
ENTRYPOINT ["/worker"]
