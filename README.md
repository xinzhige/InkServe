# InkServe
Handwriting recognition web app with a TypeScript front end and a Java 17 back end using a pretrained MNIST model via DJL.

## Features
- Draw digits (0–9) on a canvas and get model predictions.
- Shows the normalized 28×28 image sent to the model.
- Top‑K probabilities returned by the API.

## Structure
- `backend/` Spring Boot 3 (Java 17) inference API
- `frontend/` Vite + TypeScript drawing UI

## Requirements
- Java 17+
- Maven 3.9+
- Node.js 18+

## Run locally
Backend:
```bash
cd backend
mvn spring-boot:run
```
The first run downloads the MNIST model to the local DJL cache.

Frontend:
```bash
cd frontend
npm install
npm run dev
```
Open the URL printed by Vite (typically `http://localhost:5173`).

## API
`POST /api/recognize`

Request body:
```json
{
  "imageBase64": "data:image/png;base64,..."
}
```

Response:
```json
{
  "prediction": "7",
  "topScores": [
    { "label": "7", "probability": 0.91 },
    { "label": "1", "probability": 0.05 },
    { "label": "9", "probability": 0.02 }
  ],
  "normalizedImageBase64": "data:image/png;base64,..."
}
```

## Notes
- The pretrained MNIST model recognizes digits only (0-9). Letters are not supported.
- Accuracy depends heavily on preprocessing and drawing style (stroke thickness, loop separation).
- If the preview is empty, the API did not return a valid normalized image.

## Troubleshooting
- If the backend fails to start, run `mvn -e spring-boot:run` for full errors.
- A 500 error from `/api/recognize` usually means the model failed to process the image.

## License
See `LICENSE`.
