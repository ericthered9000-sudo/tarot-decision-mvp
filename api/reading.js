/**
 * Decision Tarot — AI-Powered Reading Endpoint
 *
 * Vercel Serverless Function (Node.js runtime)
 *
 * POST /api/reading
 *
 * Request body:
 * {
 *   "cards": [
 *     { "name": "The Fool", "name_short": "ar00", "orientation": "upright", "position": "Your Guidance",
 *       "meaning_up": "...", "meaning_rev": "..." },
 *     ...
 *   ],
 *   "question": "Should I take the new job?",
 *   "name": "James",
 *   "spread_name": "Three-Card Spread"
 * }
 *
 * Response (always HTTP 200 when the request is well-formed):
 * {
 *   "readings": [ { "position": "Situation", "text": "..." }, ... ],
 *   "synthesis": "...",
 *   "ai_generated": true | false,
 *   "message": "only present when we fell back to the template"
 * }
 *
 * Uses Groq (Llama 3.3 70B) via API. Falls back to a structured template if the
 * key is missing or the API fails — the client can always render `readings`.
 */

// Configuration
const DEFAULT_MODEL = 'openai/gpt-oss-120b'; // 2026-10-28: llama-3.3-70b-versatile was DEPRECATED by Groq (404s on this account's catalog); gpt-oss-120b verified live: chat + response_format json_object both 200 (reasoning tokens coexist fine — parser handles prose/JSON)
const GROQ_API_URL = 'https://api.groq.com/openai/v1/chat/completions';
const MAX_RETRIES = 2; // total attempts = MAX_RETRIES + 1
const TIMEOUT_MS = 9000; // 9s per attempt — Vercel Hobby caps request duration at 10s

// Spread position names, keyed by card count. The client sends cards from
// tarotapi.dev, which has no notion of spread positions, so we derive them here
// to keep the prompt accurate instead of telling the model "Position: undefined".
const POSITIONS_BY_COUNT = {
  1: ['Your Guidance'],
  3: ['The Situation', 'The Choice', 'The Outcome'],
  7: ['Present', 'Challenge', 'Goal', 'Foundation', 'Past', 'Future', 'Outcome'],
};

function positionFor(cards, card, index) {
  if (card.position) return card.position;
  const set = POSITIONS_BY_COUNT[cards.length];
  if (set && set[index]) return set[index];
  return `Card ${index + 1}`;
}

function cardLabel(card) {
  const name = card.name || card.name_short || 'Unknown card';
  return card.orientation === 'reversed' ? `${name} (Reversed)` : name;
}

// ============================================================
// PROMPT TEMPLATE
// ============================================================

const SYSTEM_PROMPT = `You are a wise tarot reader who helps people with decisions. You are not a fortune teller — you interpret cards to illuminate the user's present situation and help them see their choices more clearly.

Your style: warm, insightful, grounded in tarot symbolism, but focused on practical decision-making. Avoid vague fortune-telling. Always connect the card meanings to the USER'S SPECIFIC QUESTION.

For each card, write 2-3 sentences that:
1. Name the card and its position
2. Connect its traditional meaning to the user's question
3. Offer an actionable insight — what should they consider?

Then write a brief synthesis (3-4 sentences) that:
- Weaves the cards together into a cohesive story
- Gives the user one clear thing to reflect on
- Empowers them to trust their own judgment

Keep each card reading under 100 words. Keep the synthesis under 120 words.

OUTPUT FORMAT — respond with a single JSON object and nothing else (no markdown fences, no commentary before or after):
{"readings":[{"position":"<the position name given for that card>","text":"<2-3 sentences, plain text, no markdown>"}],"synthesis":"<3-4 sentences, plain text, no markdown>"}

Exactly one entry in "readings" per card, in the same order the cards were given.`;

const USER_PROMPT_TEMPLATE = `The seeker's name is "{name}".

Their question: "{question}"

Spread: {spread_name}

The cards drawn (in order):

{cards_section}

Please provide a personalized tarot reading that speaks directly to {name}'s question about: "{question}". Return only the JSON object described in your instructions.`;

// ============================================================
// PROMPT BUILDER
// ============================================================

function buildPrompt(cards, question, name, spreadName) {
  const cardsSection = cards
    .map((c, i) => {
      const isRev = c.orientation === 'reversed';
      const lines = [
        `Card ${i + 1}: ${cardLabel(c)}`,
        `  Position: ${positionFor(cards, c, i)}`,
        `  Meaning: ${c.meaning_up || ''}`,
      ];
      if (isRev) lines.push(`  Reversed meaning: ${c.meaning_rev || ''}`);
      return lines.join('\n');
    })
    .join('\n\n');

  return USER_PROMPT_TEMPLATE
    .replace(/\{name\}/g, name || 'Seeker')
    .replace(/\{question\}/g, question)
    .replace(/\{spread_name\}/g, spreadName || 'Tarot Spread')
    .replace(/\{cards_section\}/g, cardsSection);
}

// ============================================================
// GROQ API CALL (with bounded retries)
// ============================================================

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function callGroqOnce(promptText, apiKey) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);

  try {
    const response = await fetch(GROQ_API_URL, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: DEFAULT_MODEL,
        messages: [
          { role: 'system', content: SYSTEM_PROMPT },
          { role: 'user', content: promptText }
        ],
        temperature: 0.7,
        max_completion_tokens: 1024,
        top_p: 0.9,
        response_format: { type: 'json_object' },
      }),
      signal: controller.signal,
    });

    if (!response.ok) {
      const errorBody = await response.text().catch(() => '');
      const err = new Error(`Groq API error: ${response.status} ${response.statusText} — ${errorBody.slice(0, 300)}`);
      err.status = response.status;
      throw err;
    }

    const data = await response.json();
    const content = data.choices?.[0]?.message?.content;
    if (!content) throw new Error('Empty response from Groq API');
    return content;
  } finally {
    clearTimeout(timeout);
  }
}

async function callGroq(promptText, apiKey) {
  let lastError;
  for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
    try {
      return await callGroqOnce(promptText, apiKey);
    } catch (err) {
      lastError = err;
      console.error(`Groq attempt ${attempt + 1}/${MAX_RETRIES + 1} failed: ${err.message}`);
      // Retry only what can plausibly succeed on a second try: 429, 5xx, network/timeout.
      // A 401/400 (bad key, bad request) fails identically every time — retrying just
      // triples latency before the template fallback.
      const status = err.status;
      const retryable = !status || status === 429 || status >= 500;
      if (!retryable || attempt === MAX_RETRIES) throw err;
      await sleep(400);
    }
  }
  throw lastError;
}

// ============================================================
// PARSER: Extract structured readings from LLM output
// ============================================================

function templateLine(cards, card, index) {
  const isRev = card.orientation === 'reversed';
  const meaning = isRev
    ? (card.meaning_rev || 'A time to pause and reflect.')
    : (card.meaning_up || 'Forward movement and clarity.');
  return `**${cardLabel(card)}** — ${positionFor(cards, card, index)}. ${meaning}`;
}

function stripCodeFences(text) {
  const trimmed = String(text).trim();
  const fenced = trimmed.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i);
  return fenced ? fenced[1].trim() : trimmed;
}

/**
 * Turns the model's output into { readings: [{position, text}], synthesis }.
 * Three passes, most-structured first, so a chatty model never yields empty text:
 *   1. JSON (the requested format)
 *   2. Free text split on the card names
 *   3. Per-card template lines + whatever prose we got as the synthesis
 */
function parseReading(rawText, cards) {
  const cleaned = stripCodeFences(rawText);

  // --- Pass 1: JSON ---
  try {
    const parsed = JSON.parse(cleaned);
    if (parsed && Array.isArray(parsed.readings) && parsed.readings.length > 0) {
      const readings = cards.map((card, i) => {
        const r = parsed.readings[i] || {};
        const text = typeof r.text === 'string' ? r.text.trim() : '';
        return {
          position: (typeof r.position === 'string' && r.position.trim()) || positionFor(cards, card, i),
          text: text || templateLine(cards, card, i),
        };
      });
      const synthesis = typeof parsed.synthesis === 'string' ? parsed.synthesis.trim() : '';
      return { readings, synthesis, raw_text: rawText };
    }
  } catch (_) {
    // Not JSON — fall through to free-text parsing
  }

  // --- Pass 2: free text, split on card-name mentions ---
  const lower = cleaned.toLowerCase();
  const starts = cards.map(card => lower.indexOf(String(card.name || card.name_short || '').toLowerCase()));
  const allFound = starts.every(i => i >= 0);
  const ordered = allFound && starts.every((v, i) => i === 0 || v > starts[i - 1]);

  if (allFound && ordered && cards.length > 1) {
    // A trailing "Synthesis:" / "Summary:" paragraph belongs to the reading, not
    // to the last card — pull it out so it renders in the summary box instead.
    let proseEnd = cleaned.length;
    let synthesis = '';
    const synthMarker = cleaned.search(/(?:^|\n)\s*(?:synthesis|summary|in summary|overall)\s*[:\-]/i);
    if (synthMarker > starts[starts.length - 1]) {
      proseEnd = synthMarker;
      synthesis = cleaned
        .slice(synthMarker)
        .replace(/^\s*(?:synthesis|summary|in summary|overall)\s*[:\-]\s*/i, '')
        .replace(/\s+/g, ' ')
        .trim();
    }

    const readings = cards.map((card, i) => {
      const from = starts[i];
      const to = i + 1 < starts.length ? starts[i + 1] : proseEnd;
      const text = cleaned.slice(from, to).replace(/\s+/g, ' ').trim();
      return { position: positionFor(cards, card, i), text: text || templateLine(cards, card, i) };
    });
    return { readings, synthesis, raw_text: rawText };
  }

  if (cards.length === 1) {
    // Single card: the whole response is the reading for it.
    return {
      readings: [{ position: positionFor(cards, cards[0], 0), text: cleaned.replace(/\s+/g, ' ').trim() }],
      synthesis: '',
      raw_text: rawText,
    };
  }

  // --- Pass 3: prose we can't attribute — keep it as the synthesis ---
  const synthesisMatch = cleaned.match(/(?:synthesis|summary|in summary|overall)[:\s-]*([\s\S]+)/i);
  const synthesis = (synthesisMatch ? synthesisMatch[1] : cleaned).replace(/\s+/g, ' ').trim();
  return {
    readings: cards.map((card, i) => ({ position: positionFor(cards, card, i), text: templateLine(cards, card, i) })),
    synthesis,
    raw_text: rawText,
  };
}

// ============================================================
// FALLBACK: Template reading (if AI is unavailable)
// ============================================================

function fallbackReading(cards, question, name) {
  const readings = cards.map((card, i) => ({
    position: positionFor(cards, card, i),
    text: templateLine(cards, card, i),
  }));

  const reversedCount = cards.filter(c => c.orientation === 'reversed').length;
  let synthesis = '';

  if (reversedCount === 0) {
    synthesis = `Energy flows smoothly through this reading. All upright cards suggest clarity and forward momentum regarding "${question}". Trust the path ahead.`;
  } else if (reversedCount <= Math.ceil(cards.length / 2)) {
    synthesis = `There is a mix of energies here. The reversed cards invite ${name || 'you'} to pause and reconsider before moving forward with "${question}".`;
  } else {
    synthesis = `This reading suggests it's a time for deep reflection. Multiple reversals indicate that the path regarding "${question}" may need more contemplation before action.`;
  }

  return { readings, synthesis };
}

// ============================================================
// HANDLER (Vercel Serverless Function)
// ============================================================

export default async function handler(req, res) {
  // CORS — kept as '*' on purpose: the only thing a cross-origin caller can get
  // back is either a template reading or a generated one, never a secret. The
  // Capacitor APK calls from an opaque origin and we don't want a hard-coded
  // origin list to become a silent 404/failed-preflight on a future device.
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  res.setHeader('Cache-Control', 'no-store');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed. Use POST.' });
  }

  const { cards, question, name, spread_name } = req.body || {};

  // Validate
  if (!cards || !Array.isArray(cards) || cards.length === 0) {
    return res.status(400).json({ error: 'Missing or invalid "cards" array.' });
  }
  if (!question || typeof question !== 'string' || question.trim().length === 0) {
    return res.status(400).json({ error: 'Missing or invalid "question" field.' });
  }

  // Hard cap: a 78-card "spread" would blow the model's context and the 10s budget.
  const drawCards = cards.slice(0, 10);

  const userName = (name || 'Seeker').trim();
  const spreadName = spread_name || `${drawCards.length}-Card Spread`;

  // Get Groq API key from environment
  const groqApiKey = process.env.GROQ_API_KEY;

  if (!groqApiKey) {
    console.warn('GROQ_API_KEY not set. Using fallback template reading.');
    const result = fallbackReading(drawCards, question, userName);
    return res.status(200).json({
      ...result,
      ai_generated: false,
      message: 'AI reading unavailable — using template fallback. Set GROQ_API_KEY env var.',
    });
  }

  // Build prompt and call LLM
  try {
    const promptText = buildPrompt(drawCards, question, userName, spreadName);
    const rawText = await callGroq(promptText, groqApiKey);
    const parsed = parseReading(rawText, drawCards);

    return res.status(200).json({
      readings: parsed.readings,
      synthesis: parsed.synthesis,
      ai_generated: true,
    });
  } catch (err) {
    console.error('AI reading failed:', err.message);
    const result = fallbackReading(drawCards, question, userName);
    return res.status(200).json({
      ...result,
      ai_generated: false,
      message: `AI generation fell back to template: ${err.message}`,
    });
  }
}
