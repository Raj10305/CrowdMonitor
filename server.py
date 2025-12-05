from flask import Flask, request, jsonify
import base64
import numpy as np
import cv2
from ultralytics import YOLO

# Load YOLOv8 model (COCO pretrained)
model = YOLO("yolov8s.pt")

app = Flask(__name__)

@app.route("/detect", methods=["POST"])
def detect():
    data = request.get_json()
    print("DEBUG Incoming JSON keys:", data.keys())

    if not data or "image" not in data:
        return jsonify({"error": "no image"}), 400

    try:
        # Decode Base64 Image
        img_bytes = base64.b64decode(data["image"])
        nparr = np.frombuffer(img_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        if img is None:
            return jsonify({"error": "invalid image"}), 400

        # Run YOLO Detection
        results = model(img, conf=0.3)[0]   # ✅ confidence threshold added

        count = 0

        if results.boxes is not None:
            for box in results.boxes:
                cls_id = int(box.cls.item())      # ✅ Proper YOLOv8 tensor conversion
                conf = float(box.conf.item())    # ✅ Confidence check

                # COCO person class = 0
                if cls_id == 0 and conf > 0.35:
                    count += 1

        print("✅ People detected:", count)
        return jsonify({"count": count})

    except Exception as e:
        print("❌ SERVER ERROR:", e)
        return jsonify({"error": "server error"}), 500


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
