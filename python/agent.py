import os
import sys
import json
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

    prompt = (
        "Genera una lista de 6 a 10 anuncios o noticias interesantes para un servidor de Minecraft y "
        "de 0 a 3 eventos de 'Hora Feliz' (Happy Hour) para los días de la semana.\n\n"
        "Requisitos Críticos:\n"
        "1. Usa Google Search Grounding para buscar noticias reales, actualizaciones, datos o curiosidades "
        "relevantes de Minecraft 1.21 o 1.21.10 en internet, O TAMBIÉN casos curiosos, datos interesantes y "
        "noticias de actualidad sobre el mundo de los videojuegos en general (gaming).\n"
        "2. Al buscar las noticias, presta especial atención a eventos, lanzamientos, actualizaciones o festividades "
        "del mundo del gaming programadas para fechas concretas de la semana entrante (de lunes a domingo). Si detectas "
        "que un suceso ocurrirá en un día específico (por ejemplo, el lanzamiento de una actualización o un evento el "
        "jueves), debes programar la 'Hora Feliz' tematizada correspondiente exactamente para ese día de la semana "
        "(ej. `THURSDAY`), asegurando que las bonificaciones coincidan temporalmente con la fecha real del suceso y "
        "no se retrasen a la siguiente semana.\n"
        "3. Las 'Horas Felices' que generes deben estar plenamente FUNDAMENTADAS y tener CONCORDANCIA directa "
        "con los anuncios de noticias generados en el mismo archivo. Por ejemplo:\n"
        "   - Si un anuncio habla de que la pesca de Bacalao (Cod) ha aumentado o hay una festividad marítima en el gaming, "
        "el evento de Hora Feliz debe aumentar el precio de venta de 'COD' (porcentaje_extra = 50.0) o dar suerte (LUCK).\n"
        "   - Si un anuncio habla de minas colapsando o de una bonanza de excavación, el evento debe dar 'HASTE' (Prisa minera) "
        "o subir el precio de la piedra ('STONE') o diamantes ('DIAMOND').\n"
        "   - Si hay una noticia de escasez de alimentos o aumento de ganadería, aumenta el precio de 'PORKCHOP' o 'BEEF'.\n"
        "4. Tienes total flexibilidad sobre la cantidad de Horas Felices a inyectar (de 0 a 3). Si las noticias de esa semana "
        "son neutras o no justifican de manera obvia un evento de Hora Feliz, no generes ninguno (genera un array vacío []).\n"
        "5. Todos los textos (títulos, descripciones y mensajes) deben estar escritos en ESPAÑOL y formateados "
        "con los códigos de color tradicionales de Minecraft (ej. &a para verde, &b para cian, &e para amarillo, "
        "&d para rosa, &7 para gris, &l para negrita, &c para rojo).\n"
        "6. El output debe seguir rigurosamente el esquema JSON indicado."
    )

    try:
        print("Realizando consulta a Gemini con Search Grounding...")
        response = client.models.generate_content(
            model='gemini-2.5-flash',
            contents=prompt,
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=GeminiOutput,
                tools=[types.Tool(google_search=types.GoogleSearch())]
            )
        )

        output_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "anuncios.json"))
        print(f"Escribiendo resultado en: {output_path}")

        # Guardar el JSON directamente en el archivo anuncios.json
        data = json.loads(response.text)
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

        print("¡Proceso completado exitosamente!")

    except Exception as e:
        print(f"Error durante la generación de contenido: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
