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
    titulo: str = Field(description="Título del evento con códigos de color, ej. &d&l¡SOBRECARGA MINERA!")
    descripcion: List[str] = Field(description="Líneas de descripción con códigos de color de Minecraft")
    tipo: str = Field(description="Tipo de evento: PRECIO, EFECTO o AMBOS")
    item: str = Field(description="Nombre del material de Minecraft en mayúsculas (ej. STONE, DIAMOND, COD, BONE, PORKCHOP, BEEF) o AIR si solo es de efecto")
    porcentaje_extra: float = Field(description="Porcentaje extra para aumento de precio (ej. 25.0, 50.0). Poner 0.0 si no aplica.")
    efecto_pocion: str = Field(description="Efecto de poción en mayúsculas (ej. SPEED, HASTE, LUCK, o NONE si no aplica)")
    nivel_efecto: int = Field(description="Nivel del efecto (1, 2, 3) o 0 si no aplica")
    todo_el_dia: bool = Field(description="True si dura todo el día, False si tiene horas específicas")
    hora_inicio: int = Field(description="Hora de inicio (0-23) si todo_el_dia es False, de lo contrario 0")
    hora_fin: int = Field(description="Hora de fin (0-23) si todo_el_dia es False, de lo contrario 24")

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

    # Paso 1: Obtener la información del gaming y de Minecraft con Search Grounding
    print("Realizando Paso 1: Consultando a Gemini con Search Grounding...")
    search_prompt = (
        "Busca noticias reales de Minecraft 1.21 o 1.21.10, o casos curiosos, datos interesantes y noticias de "
        "actualidad sobre el mundo de los videojuegos en general (gaming) en internet.\n\n"
        "A partir de lo que encuentres, redacta en español:\n"
        "1. Una lista de 6 a 10 noticias/anuncios para un servidor de Minecraft, formateados con códigos de color tradicionales "
        "de Minecraft (ej. &a para verde, &b para cian, &e para amarillo, &d para rosa, &7 para gris, &l para negrita, &c para rojo).\n"
        "2. De 0 a 3 eventos de 'Hora Feliz' (Happy Hour) para los días de la semana entrante (de lunes a domingo) "
        "que estén plenamente FUNDAMENTADAS y tengan CONCORDANCIA directa con las noticias generadas.\n"
        "Por ejemplo, si una noticia habla de abundancia de pesca, crea un evento para el bacalao (COD); si habla de minería, "
        "un evento de piedra (STONE), diamantes (DIAMOND) o prisa minera (HASTE); si habla de escasez de carne o ganadería, "
        "aumenta el precio de BEEF o PORKCHOP.\n"
        "Presta especial atención si detectas que un suceso ocurrirá en un día específico (por ejemplo, el lanzamiento de una "
        "actualización o un evento el jueves 5 de agosto), para que programes la 'Hora Feliz' tematizada exactamente para ese "
        "día de la semana (ej. `THURSDAY`), asegurando que coincidan temporalmente.\n\n"
        "REGLAS CRÍTICAS PARA LOS CAMPOS MECÁNICOS DEL EVENTO (NUNCA USES NONE/0/AIR SI EL EVENTO DESCRIBE UN BENEFICIO):\n"
        "- Si el evento otorga un efecto de poción en su descripción (ej. velocidad, prisa minera), el tipo DEBE ser 'EFECTO' o 'AMBOS', "
        "el campo `efecto_pocion` DEBE ser un efecto válido de Minecraft en mayúsculas (ej: SPEED, HASTE, LUCK, STRENGTH, REGENERATION) "
        "y `nivel_efecto` DEBE ser un número entero entre 1 y 3 (ej. 1 o 2). NUNCA uses 'NONE' ni '0' si la descripción habla de un efecto.\n"
        "- Si el evento otorga un beneficio en el precio o venta de un ítem en su descripción (ej. vender cobre, diamantes o comida más caro), "
        "el tipo DEBE ser 'PRECIO' o 'AMBOS', el campo `item` DEBE ser un material de Minecraft en mayúsculas (ej: COPPER_INGOT, RAW_COPPER, "
        "DIAMOND, STONE, COD, BEEF, PORKCHOP, EMERALD) y `porcentaje_extra` DEBE ser un porcentaje mayor a 0 (ej: 25.0, 50.0). NUNCA uses 'AIR' "
        "ni '0.0' si se describe un beneficio comercial.\n"
        "- Si el evento describe tanto un efecto de poción como un beneficio de tienda (ej: prisa minera y diamantes valen más), el tipo "
        "DEBE ser 'AMBOS', y debes completar todos los campos descritos arriba."
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
                f"Toma la información de noticias y eventos de Hora Feliz redactada a continuación, organízala y "
                f"estrustúrala estrictamente en el formato JSON correspondiente al esquema indicado. Conserva todos "
                f"los códigos de color de Minecraft y los identificadores únicos.\n\n"
                f"REGLAS DE FORMATO MECÁNICO OBLIGATORIAS:\n"
                f"- Si el evento es de tipo 'EFECTO' o 'AMBOS', `efecto_pocion` NO puede ser 'NONE' y `nivel_efecto` NO puede ser 0. Debes mapearlo a un efecto real (ej: SPEED, HASTE, STRENGTH, LUCK) y nivel (ej: 1, 2).\n"
                f"- Si el evento es de tipo 'PRECIO' o 'AMBOS', `item` NO puede ser 'AIR' y `porcentaje_extra` NO puede ser 0.0. Mapea un material real (ej: COPPER_INGOT, RAW_COPPER, DIAMOND, COD) y porcentaje (ej: 25.0, 50.0).\n"
                f"- Revisa las descripciones textuales del borrador y asocia los campos mecánicos reales correspondientes.\n\n"
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

            # Guardar el JSON directamente en el archivo anuncios.json
            data = json.loads(response_step2.text)
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
