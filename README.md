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

## Architecture (SVG)
```svg
<svg width="820" height="300" viewBox="0 0 820 300" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="InkServe architecture diagram">
  <defs>
    <style>
      .box { fill:#fff7eb; stroke:#1d1c1a; stroke-width:2; }
      .title { font: 600 15px 'Space Grotesk', Arial, sans-serif; fill:#1d1c1a; }
      .text { font: 13px 'Space Grotesk', Arial, sans-serif; fill:#3b3733; }
      .arrow { stroke:#1d1c1a; stroke-width:2; marker-end:url(#arrowhead); }
    </style>
    <marker id="arrowhead" markerWidth="10" markerHeight="8" refX="9" refY="4" orient="auto">
      <polygon points="0 0, 10 4, 0 8" fill="#1d1c1a"/>
    </marker>
  </defs>

  <rect class="box" x="30" y="40" rx="14" ry="14" width="220" height="110"/>
  <text class="title" x="50" y="70">Frontend</text>
  <text class="text" x="50" y="95">Vite + TypeScript</text>
  <text class="text" x="50" y="115">Canvas UI</text>

  <rect class="box" x="300" y="40" rx="14" ry="14" width="220" height="110"/>
  <text class="title" x="320" y="70">Backend</text>
  <text class="text" x="320" y="95">Spring Boot</text>
  <text class="text" x="320" y="115">Preprocess + Infer</text>

  <rect class="box" x="570" y="40" rx="14" ry="14" width="220" height="110"/>
  <text class="title" x="590" y="70">DJL Model Zoo</text>
  <text class="text" x="590" y="95">PyTorch Engine</text>
  <text class="text" x="590" y="115">MNIST MLP</text>

  <line class="arrow" x1="250" y1="95" x2="300" y2="95"/>
  <text class="text" x="252" y="80">POST</text>

  <line class="arrow" x1="520" y1="95" x2="570" y2="95"/>
  <text class="text" x="525" y="80">Model</text>

  <rect class="box" x="300" y="195" rx="12" ry="12" width="220" height="70"/>
  <text class="title" x="320" y="225">Response</text>
  <text class="text" x="320" y="245">Pred + Top‑K + Img</text>
  <line class="arrow" x1="410" y1="150" x2="410" y2="195"/>
</svg>
```

## Troubleshooting
- If the backend fails to start, run `mvn -e spring-boot:run` for full errors.
- A 500 error from `/api/recognize` usually means the model failed to process the image.

## License
See `LICENSE`.
