from flask import Flask, request, jsonify, send_from_directory
import base64
import numpy as np
import cv2
import uuid
import os
from ultralytics import YOLO
from database import init_db, insert_detection, fetch_history

app = Flask(__name__)

# Directory where uploaded frames are saved
UPLOAD_DIR = "static/uploads/"
os.makedirs(UPLOAD_DIR, exist_ok=True)

# Load YOLOv8 model once (fast)
model = YOLO("yolov8l.pt")

# Initialize SQLite DB
init_db()


# ---- PERSON DETECTION ENDPOINT ----
@app.route("/detect", methods=["POST"])
def detect():
    data = request.get_json()

    if not data or "image" not in data:
        return jsonify({"error": "no image"}), 400

    try:
        # Decode Base64 image
        img_bytes = base64.b64decode(data["image"])
        nparr = np.frombuffer(img_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        # Run model
        results = model(img, conf=0.3)[0]

        # Count persons (class id=0)
        count = sum(1 for box in results.boxes if int(box.cls.item()) == 0)

        # Save image to disk
        filename = f"{uuid.uuid4().hex}.jpg"
        cv2.imwrite(os.path.join(UPLOAD_DIR, filename), img)

        # Save record to database
        insert_detection(count, filename)

        return jsonify({"count": count})

    except Exception as e:
        print("SERVER ERROR:", e)
        return jsonify({"error": "server error"}), 500


# ---- HISTORY ENDPOINT ----
@app.route("/history", methods=["GET"])
def history():
    rows = fetch_history()
    output = []

    for ts, count, filename in rows:
        output.append({
            "timestamp": ts,
            "count": count,
            "filename": filename,
            "url": f"/static/uploads/{filename}"
        })

    return jsonify({"history": output})


# ---- SERVE IMAGE FILES FOR ANDROID UI ----
@app.route("/static/uploads/<filename>")
def serve_image(filename):
    return send_from_directory(UPLOAD_DIR, filename)


# ---- RUN SERVER ----
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)

