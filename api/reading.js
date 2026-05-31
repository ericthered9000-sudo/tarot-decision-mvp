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
 *     { "name": "The Fool", "name_short": "ar_fool", "orientation": "upright", "position": "Your Guidance", "guide": "..." },
 *     ...
 *   ],
 *   "question": "Should I take the new job?",
 *   "name": "James",
 *   "spread_name": "Three-Card Spread"
 * }
 * 
 * Response:
 * {
 *   "readings": [
 *     { "position": "Situation", "text": "..." },
 *     ...
 *   ],
 *   "synthesis": "..."
 * }
 * 
 * Uses Groq (Llama 3.3 70B) via API. Falls back to structured template if API fails.
 */

// Configuration
const DEFAULT_MODEL = 'llama-3.3-70b-versatile';
const GROQ_API_URL = 'https://api.groq.com/openai/v1/chat/completions';
const MAX_RETRIES = 2;
const TIMEOUT_MS = 15000; // 15s — Vercel Hobby timeout is 10s, so keep it tight

// ============================================================
// PROMPT TEMPLATE
// ============================================================

const SYSTEM_PROMPT = `You are a wise tarot reader who helps people with decisions. You are not a fortune teller — you interpret cards to illuminate the user's present situation and help them see their choices more clearly.

Your style: warm, insightful, grounded in tarot symbolism, but focused on practical decision-making. Avoid vague fortune-telling. Always connect the card meanings to the USER'S SPECIFIC QUESTION.

For each card, provide 2-3 sentences that:
1. Name the card and its position
2. Connect its traditional meaning to the user's question
3. Offer an actionable insight — what should they consider?

End with a brief synthesis (3-4 sentences) that:
- Weaves the cards together into a cohesive story
- Gives the user one clear thing to reflect on
- Empowers them to trust their own judgment

Keep each card reading under 100 words. Keep the synthesis under 120 words.`;

const USER_PROMPT_TEMPLATE = `The seeker's name is "{name}".

Their question: "{question}"

Spread: {spread_name}

The cards drawn (in order):

{cards_section}

Please provide a personalized tarot reading that speaks directly to {name}'s question about: "{question}".`;

// ============================================================
// PROMPT BUILDER
// ============================================================

function buildPrompt(cards, question, name, spreadName) {
  const cardsSection = cards
    .map((c, i) => {
      const orientation = c.orientation || 'upright';
      const revTag = orientation === 'reversed' ? ' (Reversed)' : '';
      return `Card ${i + 1}: ${c.name || c.name_short}${revTag}
  Position: ${c.position}
  Meaning: ${c.meaning_up || ''}
  ${revTag ? `Reversed meaning: ${c.meaning_rev || ''}` : ''}`;
    })
    .join('\n\n');

  return USER_PROMPT_TEMPLATE
    .replace(/\{name\}/g, name || 'Seeker')
    .replace(/\{question\}/g, question)
    .replace(/\{spread_name\}/g, spreadName || 'Tarot Spread')
    .replace(/\{cards_section\}/g, cardsSection);
}

// ============================================================
// GROQ API CALL
// ============================================================

async function callGroq(promptText, apiKey) {
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
      }),
      signal: controller.signal,
    });

    if (!response.ok) {
      const errorBody = await response.text().catch(() => '');
      throw new Error(`Groq API error: ${response.status} ${response.statusText} — ${errorBody}`);
    }

    const data = await response.json();
    const content = data.choices?.[0]?.message?.content;
    if (!content) throw new Error('Empty response from Groq API');
    return content;
  } finally {
    clearTimeout(timeout);
  }
}

// ============================================================
// PARSER: Extract structured readings from LLM output
// ============================================================

function parseReading(rawText, cards) {
  // Try to parse as structured JSON first (in case model outputs that)
  try {
    const parsed = JSON.parse(rawText);
    if (parsed.readings && Array.isArray(parsed.readings)) return parsed;
  } catch (_) {
    // Not JSON — parse as free text
  }

  // Free-text parsing: split into card sections and synthesis
  const lines = rawText.split('\n').filter(l => l.trim());
  
  const readings = cards.map((card, i) => {
    const cardName = card.name || card.name_short;
    // Find text belonging to this card by looking for nearby references
    // We'll extract everything from the start and divide roughly
    return {
      position: card.position,
      text: `**${cardName}${card.orientation === 'reversed' ? ' (Reversed)' : ''}** — ${card.position}`,
    };
  });

  // Find synthesis: look for lines with "synthesis", "summary", "in summary", "overall"
  const synthesisStart = lines.findIndex(l =>
    /synthesis|summary|in summary|overall|together|woven|reflection/i.test(l)
  );

  let synthesis = '';
  if (synthesisStart >= 0) {
    synthesis = lines.slice(synthesisStart).join('\n');
  }

  return { readings, synthesis, raw_text: rawText };
}

// ============================================================
// FALLBACK: Template reading (if AI is unavailable)
// ============================================================

function fallbackReading(cards, question, name) {
  const readings = cards.map(card => {
    const isRev = card.orientation === 'reversed';
    const meaning = isRev ? (card.meaning_rev || 'A time to pause and reflect.') : (card.meaning_up || 'Forward movement and clarity.');
    return {
      position: card.position,
      text: `**${card.name}${isRev ? ' (Reversed)' : ''}** — ${card.position}. ${meaning}`,
    };
  });

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
  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

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

  const userName = (name || 'Seeker').trim();
  const spreadName = spread_name || `${cards.length}-Card Spread`;

  // Get Groq API key from environment
  const groqApiKey = process.env.GROQ_API_KEY;

  if (!groqApiKey) {
    console.warn('GROQ_API_KEY not set. Using fallback template reading.');
    const result = fallbackReading(cards, question, userName);
    return res.status(200).json({
      ...result,
      ai_generated: false,
      message: 'AI reading unavailable — using template fallback. Set GROQ_API_KEY env var.',
    });
  }

  // Build prompt and call LLM
  try {
    const promptText = buildPrompt(cards, question, userName, spreadName);
    const rawText = await callGroq(promptText, groqApiKey);
    const parsed = parseReading(rawText, cards);

    return res.status(200).json({
      readings: parsed.readings,
      synthesis: parsed.synthesis,
      raw_text: parsed.raw_text,
      ai_generated: true,
    });
  } catch (err) {
    console.error('AI reading failed:', err.message);
    const result = fallbackReading(cards, question, userName);
    return res.status(200).json({
      ...result,
      ai_generated: false,
      message: `AI generation fell back to template: ${err.message}`,
    });
  }
}
