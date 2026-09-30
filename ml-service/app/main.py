"""Điểm vào của ml-service: khởi tạo ứng dụng FastAPI và endpoint kiểm tra sức khỏe."""

from fastapi import FastAPI

app = FastAPI(title="Fashion Shop ML Service")


@app.get("/health")
def health() -> dict[str, str]:
    """Trả về trạng thái service — cùng định dạng với /actuator/health của Spring Boot."""
    return {"status": "UP"}
