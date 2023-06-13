FROM rust:1.67

RUN apt-get update && apt-get install -y libudev-dev

WORKDIR /usr/src/rsoxy
COPY . .

RUN cargo install --path .

CMD ["rsoxy"]
