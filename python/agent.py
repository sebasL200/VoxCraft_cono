import os
import sys
import json
import time
from pydantic import BaseModel, Field
from typing import List
from google import genai
from google.genai import types

# Definir la estructura esperada usando Pydantic
class Announcement(BaseModel):
    id: str = Field(description="Identificador único en formato snake_case, ej. noticia_veta_diamante")
    categoria: str = Field(description="Categoría del anuncio, ej. noticias, consejos, curiosidades")
    mensajes: List[str] = Field(description="Líneas del mensaje formateadas con códigos de color de Minecraft (ej. &a, &e, &7, &b, &l)")

class HappyHourEvent(BaseModel):
    dia: str = Field(description="Día de la semana en mayúsculas: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY")
    titulo: str = Field(description="Título creativo y temático del evento con códigos de color de Minecraft. Ej: &6&l¡Día del Forjador! o &a&l¡Festín de Bacalao!. NUNCA uses frases genéricas como 'HAPPY HOUR', 'HORA FELIZ' ni el nombre del día en el título.")
    descripcion: List[str] = Field(description="1 a 2 líneas explicando por qué ese día es especial, vinculadas a una noticia real. Con códigos de color de Minecraft.")
    tipo: str = Field(description="Tipo de evento: PRECIO, EFECTO, o AMBOS. El Evento 1 de la lista debe ser PRECIO o AMBOS. El Evento 2 debe ser EFECTO o AMBOS.")
    item: str = Field(description="Material exacto de Minecraft (mayúsculas) de esta lista: [COPPER_INGOT, RAW_COPPER, COPPER_BLOCK, TUFF, DIAMOND, STONE, COD, BEEF, PORKCHOP, EMERALD, GOLD_INGOT, IRON_INGOT, COAL, BONE, WHEAT]. Obligatorio si tipo es PRECIO o AMBOS. Usa 'AIR' solo si tipo es EFECTO puro.")
    porcentaje_extra: float = Field(description="Porcentaje de bonus de precio (ej. 25.0, 50.0). Obligatorio y mayor a 0 si tipo es PRECIO o AMBOS. Usa 0.0 solo si tipo es EFECTO puro.")
    efecto_pocion: str = Field(description="Efecto de poción exacto (mayúsculas) de esta lista: [SPEED, HASTE, LUCK, STRENGTH, REGENERATION, RESISTANCE, FIRE_RESISTANCE, WATER_BREATHING, NIGHT_VISION]. Obligatorio si tipo es EFECTO o AMBOS. Usa 'NONE' solo si tipo es PRECIO puro.")
    nivel_efecto: int = Field(description="Nivel del efecto: 1, 2 o 3. Obligatorio y >= 1 si tipo es EFECTO o AMBOS. Usa 0 solo si tipo es PRECIO puro.")
    todo_el_dia: bool = Field(description="True si dura todo el día, False si tiene horas específicas")
    hora_inicio: int = Field(description="Hora de inicio (0-23). Usar 0 si todo_el_dia es True.")
    hora_fin: int = Field(description="Hora de fin (1-24). Usar 24 si todo_el_dia es True.")

class GeminiOutput(BaseModel):
    anuncios: List[Announcement]
    horas_felices: List[HappyHourEvent]

def main():
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        print("Error: La variable de entorno GEMINI_API_KEY no está configurada.", file=sys.stderr)
        sys.exit(1)

    print("Iniciando cliente de Gemini...")
    client = genai.Client(api_key=api_key)

    # Paso 1: Búsqueda de noticias con Google Search Grounding
    print("Realizando Paso 1: Consultando a Gemini con Search Grounding...")
    search_prompt = (
        "Busca noticias reales y recientes de Minecraft (especialmente versiones 1.21.x), datos curiosos del mundo "
        "del gaming en general, y lanzamientos de videojuegos de esta semana en internet.\n\n"
        "A partir de lo que encuentres, redacta en español:\n"
        "1. Una lista de 6 a 10 noticias/anuncios para un servidor de Minecraft, formateados con códigos de color "
        "de Minecraft (&a verde, &b cian, &e amarillo, &d rosa, &7 gris, &l negrita, &c rojo).\n"
        "2. EXACTAMENTE 2 eventos de 'Hora Feliz' para 2 días DIFERENTES de la semana entrante, "
        "plenamente FUNDAMENTADOS en alguna de las noticias que encontraste:\n"
        "   - EVENTO 1 (PRECIO): Debe ser de tipo PRECIO, con un ítem de la tienda que valga más ese día. "
        "Con un 30% de probabilidad puede ser AMBOS (precio + efecto de poción también).\n"
        "   - EVENTO 2 (EFECTO): Debe ser de tipo EFECTO, con un efecto de poción que los jugadores recibirán. "
        "Con un 30% de probabilidad puede ser AMBOS (efecto + precio de un ítem también).\n\n"
        "REGLAS CRÍTICAS PARA TÍTULOS Y CAMPOS MECÁNICOS:\n"
        "- TÍTULO: Crea un nombre inmersivo y temático que explique el motivo (ej. &6&l¡Día del Forjador!, "
        "&b&l¡Fiebre del Cobre!, &c&l¡La Gran Cacería!). NUNCA uses 'HAPPY HOUR', 'HORA FELIZ' ni el nombre del día.\n"
        "- DESCRIPCIÓN: 1-2 líneas que digan explícitamente POR QUÉ ese día es especial (vincúlalo a la noticia real).\n"
        "- Materiales válidos para `item`: [COPPER_INGOT, RAW_COPPER, COPPER_BLOCK, TUFF, DIAMOND, STONE, "
        "COD, BEEF, PORKCHOP, EMERALD, GOLD_INGOT, IRON_INGOT, COAL, BONE, WHEAT]. NUNCA uses AIR si el tipo tiene precio.\n"
        "- Efectos válidos para `efecto_pocion`: [SPEED, HASTE, LUCK, STRENGTH, REGENERATION, RESISTANCE, "
        "FIRE_RESISTANCE, WATER_BREATHING, NIGHT_VISION]. NUNCA uses NONE si el tipo tiene efecto.\n"
        "- Si el tipo es AMBOS, debes completar TANTO item+porcentaje_extra COMO efecto_pocion+nivel_efecto.\n"
        "- Si el tipo es PRECIO puro: usa 'AIR', 0.0 para los campos de efecto. Si es EFECTO puro: usa 'AIR', 0.0 para precio."
    )

    max_retries = 5
    retry_delay = 10  # segundos de espera entre reintentos

    # --- PASO 1 ---
    raw_text = ""
    for intento in range(max_retries):
        try:
            print(f"Realizando Paso 1: Consultando a Gemini con Search Grounding (Intento {intento + 1}/{max_retries})...")
            response_step1 = client.models.generate_content(
                model='gemini-2.5-flash',
                contents=search_prompt,
                config=types.GenerateContentConfig(
                    tools=[types.Tool(google_search=types.GoogleSearch())]
                )
            )
            raw_text = response_step1.text
            print("Paso 1 completado. Texto borrador generado.")
            break
        except Exception as e:
            if "503" in str(e) or "UNAVAILABLE" in str(e) or "429" in str(e):
                if intento < max_retries - 1:
                    print(f"Advertencia: Servidor de Gemini saturado o no disponible ({e}). Reintentando en {retry_delay} segundos...")
                    time.sleep(retry_delay)
                    continue
            print(f"Error crítico en el Paso 1: {e}", file=sys.stderr)
            sys.exit(1)

    # --- PASO 2 ---
    for intento in range(max_retries):
        try:
            print(f"Realizando Paso 2: Estructurando el contenido en formato JSON (Intento {intento + 1}/{max_retries})...")
            structure_prompt = (
                f"Toma la información de noticias y 2 eventos de Hora Feliz redactada a continuación y "
                f"conviértela estrictamente al formato JSON del esquema indicado.\n\n"
                f"REGLAS DE VALIDACIÓN PARA EL JSON:\n"
                f"- La lista `horas_felices` debe tener EXACTAMENTE 2 eventos en días DISTINTOS.\n"
                f"- El Evento 1 debe ser tipo PRECIO o AMBOS. El Evento 2 debe ser tipo EFECTO o AMBOS.\n"
                f"- TÍTULO: Nunca uses 'HAPPY HOUR' ni 'HORA FELIZ'. Usa un nombre creativo y temático.\n"
                f"- Si tipo es PRECIO o AMBOS: `item` NO puede ser 'AIR' (usa COPPER_INGOT, DIAMOND, COD, BEEF, etc.) "
                f"y `porcentaje_extra` debe ser > 0.\n"
                f"- Si tipo es EFECTO o AMBOS: `efecto_pocion` NO puede ser 'NONE' (usa SPEED, HASTE, LUCK, etc.) "
                f"y `nivel_efecto` debe ser 1, 2 o 3.\n"
                f"- Si tipo es PRECIO puro: `efecto_pocion`='NONE', `nivel_efecto`=0.\n"
                f"- Si tipo es EFECTO puro: `item`='AIR', `porcentaje_extra`=0.0.\n\n"
                f"Borrador de texto:\n{raw_text}"
            )

            response_step2 = client.models.generate_content(
                model='gemini-2.5-flash',
                contents=structure_prompt,
                config=types.GenerateContentConfig(
                    response_mime_type="application/json",
                    response_schema=GeminiOutput
                )
            )

            output_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "anuncios.json"))
            print(f"Escribiendo resultado en: {output_path}")

            data = json.loads(response_step2.text)

            # Validación: asegurar que hay exactamente 2 horas felices
            hf = data.get("horas_felices", [])
            if len(hf) != 2:
                print(f"Advertencia: Se esperaban 2 eventos de Hora Feliz pero se obtuvieron {len(hf)}. Continuando de todas formas.")

            with open(output_path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)

            print("¡Proceso completado exitosamente!")
            break
        except Exception as e:
            if "503" in str(e) or "UNAVAILABLE" in str(e) or "429" in str(e):
                if intento < max_retries - 1:
                    print(f"Advertencia: Servidor de Gemini saturado o no disponible ({e}). Reintentando en {retry_delay} segundos...")
                    time.sleep(retry_delay)
                    continue
            print(f"Error crítico en el Paso 2: {e}", file=sys.stderr)
            sys.exit(1)

if __name__ == "__main__":
    main()
