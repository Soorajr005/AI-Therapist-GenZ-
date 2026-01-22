from fastapi import FastAPI
from pydantic import BaseModel
import os
from groq import Groq

# API Key 
GROQ_API_KEY = "Api_key"
client = Groq(api_key=GROQ_API_KEY)

app = FastAPI(title="Gen Z AI Therapist API")


chat_history = []

SYSTEM_PERSONA = """
You are a Gen Z AI Therapist. You are NOT a clinical robot.
- Speak in casual, lower-case internet slang.
- Use emojis sparingly.
- Be empathetic but chill.
- Remember what the user told you previously.
"""

# Initialize history
chat_history.append({"role": "system", "content": SYSTEM_PERSONA})

class ChatRequest(BaseModel):
    user_message: str

@app.get("/")
def home():
    return {"message": "AI Therapist is online (with Memory!)"}

@app.post("/chat/")
async def chat_endpoint(request: ChatRequest):
    # 1. Add  new message to history
    chat_history.append({"role": "user", "content": request.user_message})
    
    # 2. Send the Entire history to the model
    completion = client.chat.completions.create(
        model="llama-3.1-8b-instant",
        messages=chat_history,
        temperature=0.7,
    )
    
    ai_reply = completion.choices[0].message.content
    
    # 3. Save models reply to history
    chat_history.append({"role": "assistant", "content": ai_reply})
    
    return {"reply": ai_reply}

@app.post("/reset/")
def reset_memory():
    global chat_history
    chat_history = [{"role": "system", "content": SYSTEM_PERSONA}]
    return {"message": "Memory wiped."}