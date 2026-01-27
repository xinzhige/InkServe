import './style.css';

type Score = { label: string; probability: number };

type RecognizeResponse = {
  prediction: string;
  topScores: Score[];
  normalizedImageBase64: string;
};

const app = document.querySelector<HTMLDivElement>('#app');
if (!app) throw new Error('Missing app root');

app.innerHTML = `
  <div class="page">
    <header>
      <div>
        <p class="eyebrow">InkServe</p>
        <h1>Handwriting to Formal Digit</h1>
        <p class="subhead">Draw a digit (0-9) on the left. The right panel shows the normalized 28x28 input and model prediction.</p>
      </div>
      <div class="cta">
        <button id="clearBtn" class="ghost">Clear</button>
        <button id="predictBtn" class="primary">Recognize</button>
      </div>
    </header>

    <main class="grid">
      <section class="panel">
        <h2>Input</h2>
        <div class="canvas-wrap">
          <canvas id="drawCanvas" width="240" height="240"></canvas>
          <div class="hint">Draw with your mouse or touch. Digits only (MNIST).</div>
        </div>
      </section>

      <section class="panel">
        <h2>Formalized</h2>
        <div class="output">
          <div class="preview">
            <img id="normalizedImg" alt="Normalized 28x28" />
          </div>
          <div class="stats">
            <div class="stat">
              <span class="label">Prediction</span>
              <span id="prediction" class="value">—</span>
            </div>
            <div class="stat">
              <span class="label">Confidence</span>
              <span id="confidence" class="value">—</span>
            </div>
            <div class="scores">
              <span class="label">Top Scores</span>
              <ul id="scoreList"></ul>
            </div>
          </div>
        </div>
        <div id="status" class="status">Waiting for input...</div>
      </section>
    </main>
  </div>
`;

const canvas = document.querySelector<HTMLCanvasElement>('#drawCanvas')!;
const clearBtn = document.querySelector<HTMLButtonElement>('#clearBtn')!;
const predictBtn = document.querySelector<HTMLButtonElement>('#predictBtn')!;
const normalizedImg = document.querySelector<HTMLImageElement>('#normalizedImg')!;
const prediction = document.querySelector<HTMLSpanElement>('#prediction')!;
const confidence = document.querySelector<HTMLSpanElement>('#confidence')!;
const scoreList = document.querySelector<HTMLUListElement>('#scoreList')!;
const status = document.querySelector<HTMLDivElement>('#status')!;

const ctx = canvas.getContext('2d');
if (!ctx) throw new Error('Canvas not supported');
normalizedImg.classList.add('hidden');

const size = canvas.width;
ctx.fillStyle = '#000';
ctx.fillRect(0, 0, size, size);
ctx.strokeStyle = '#fff';
ctx.lineWidth = 8;
ctx.lineCap = 'round';
ctx.lineJoin = 'round';

let drawing = false;
let lastX = 0;
let lastY = 0;

const startDrawing = (x: number, y: number) => {
  drawing = true;
  lastX = x;
  lastY = y;
};

const draw = (x: number, y: number) => {
  if (!drawing) return;
  ctx.beginPath();
  ctx.moveTo(lastX, lastY);
  ctx.lineTo(x, y);
  ctx.stroke();
  lastX = x;
  lastY = y;
};

const stopDrawing = () => {
  drawing = false;
};

const getCanvasCoords = (evt: PointerEvent) => {
  const rect = canvas.getBoundingClientRect();
  return {
    x: ((evt.clientX - rect.left) / rect.width) * canvas.width,
    y: ((evt.clientY - rect.top) / rect.height) * canvas.height,
  };
};

canvas.addEventListener('pointerdown', (evt) => {
  canvas.setPointerCapture(evt.pointerId);
  const { x, y } = getCanvasCoords(evt);
  startDrawing(x, y);
});

canvas.addEventListener('pointermove', (evt) => {
  const { x, y } = getCanvasCoords(evt);
  draw(x, y);
});

canvas.addEventListener('pointerup', () => stopDrawing());
canvas.addEventListener('pointerleave', () => stopDrawing());

clearBtn.addEventListener('click', () => {
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, size, size);
  ctx.fillStyle = '#000';
  prediction.textContent = '—';
  confidence.textContent = '—';
  scoreList.innerHTML = '';
  normalizedImg.removeAttribute('src');
  normalizedImg.classList.add('hidden');
  status.textContent = 'Canvas cleared.';
});

const renderScores = (scores: Score[]) => {
  scoreList.innerHTML = '';
  scores.forEach((score) => {
    const li = document.createElement('li');
    li.textContent = `${score.label} • ${(score.probability * 100).toFixed(1)}%`;
    scoreList.appendChild(li);
  });
};

const setStatus = (message: string) => {
  status.textContent = message;
};

normalizedImg.addEventListener('error', () => {
  normalizedImg.removeAttribute('src');
  normalizedImg.classList.add('hidden');
});

const recognize = async () => {
  try {
    setStatus('Sending to model...');
    predictBtn.disabled = true;

    const payload = canvas.toDataURL('image/png');
    const response = await fetch('http://localhost:8080/api/recognize', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ imageBase64: payload }),
    });

    if (!response.ok) {
      throw new Error(`Server error: ${response.status}`);
    }

    const data = (await response.json()) as RecognizeResponse;
    prediction.textContent = data.prediction;
    confidence.textContent = data.topScores?.[0]
      ? `${(data.topScores[0].probability * 100).toFixed(1)}%`
      : '—';
    if (data.normalizedImageBase64?.startsWith('data:image')) {
      normalizedImg.classList.remove('hidden');
      normalizedImg.src = data.normalizedImageBase64;
    } else {
      normalizedImg.removeAttribute('src');
      normalizedImg.classList.add('hidden');
    }
    renderScores(data.topScores ?? []);
    setStatus('Recognition complete.');
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Unknown error';
    setStatus(`Failed to recognize: ${message}`);
  } finally {
    predictBtn.disabled = false;
  }
};

predictBtn.addEventListener('click', recognize);
