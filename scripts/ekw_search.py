"""
LU1I/00016057/7

Używa Selenium z Chrome WebDriver i opcjami anti-detection,
aby ominąć ochronę Incapsula/Imperva.
"""

import time
import sys
import random

import sqlite3
from pathlib import Path
from datetime import datetime

from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from webdriver_manager.chrome import ChromeDriverManager


EKW_URL = (
    "https://przegladarka-ekw.ms.gov.pl/eukw_prz/KsiegiWieczyste/"
    "wyszukiwanieKW?komunikaty=true&kontakt=true&okienkoSerwisowe=false"
)

KW_DEPARTMENT_DEF = "LU1I"
KW_NUMBER_DEF = "00016057"


DB_PATH = "../results/visited.db"


# --- Inicjalizacja bazy ---
def init_db():
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    c.execute("""CREATE TABLE IF NOT EXISTS visited (number INTEGER PRIMARY KEY,visited_at TEXT)""")
    conn.commit()
    return conn

# --- Sprawdzenie czy numer już odwiedzony ---
def is_visited(conn, num):
    c = conn.cursor()
    c.execute("SELECT 1 FROM visited WHERE number=?", (num,))
    return c.fetchone() is not None

# --- Zapis informacji o odwiedzeniu ---
def mark_visited(conn, num):
    c = conn.cursor()
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    c.execute(
        "INSERT OR IGNORE INTO visited (number, visited_at) VALUES (?, ?)",
        (num, timestamp)
    )
    conn.commit()


def calculate_kw_checksum(kw_dep: str, kw_number: str) -> int:
    """
    Wylicza cyfrę kontrolną księgi wieczystej.

    Algorytm:
    - Łączy kod wydziału (4 znaki) z numerem księgi (8 cyfr) w 12-znakowy ciąg.
    - Każdy znak mnożony jest przez odpowiednią wagę z cyklu [1, 3, 7].
    - Litery zamieniane są na wartości 1-9 (pozycja w alfabecie mod 9, z mapowaniem 0->9).
    - Cyfra kontrolna to suma ważona mod 10.
    """
    input_str = (kw_dep + kw_number).upper()
    if len(input_str) != 12:
        raise ValueError(
            f"kod_wydzialu (4 znaki) + numer_ksiegi (8 znaków) musi mieć 12 znaków, otrzymano: {len(input_str)}"
        )

    weights = [1, 3, 7, 1, 3, 7, 1, 3, 7, 1, 3, 7]
    total = 0

    for char, weight in zip(input_str, weights):
        if char.isdigit():
            value = int(char)
        elif char.isalpha():
            alphabet_position = ord(char) - ord('A') + 1
            value = ((alphabet_position - 1) % 9) + 1
        else:
            raise ValueError(f"Nieprawidłowy znak w danych wejściowych: {char}")
        total += value * weight

    return total % 10

def hcaptcha_present(driver):
    try:
        # Look for iframe from hcaptcha.com
        iframes = driver.find_elements(By.TAG_NAME, "iframe")
        for iframe in iframes:
            src = iframe.get_attribute("src") or ""
            if "hcaptcha.com" in src:
                return True
        return False
    except Exception:
        return False



def create_driver(headless: bool = False) -> webdriver.Chrome:
    options = Options()
    if headless:
        options.add_argument("--headless=new")
    options.add_argument("--disable-gpu")
    options.add_argument("--no-sandbox")
    options.add_argument("--window-size=1920,1080")
    # Anti-detection options
    options.add_argument("--disable-blink-features=AutomationControlled")
    options.add_experimental_option("excludeSwitches", ["enable-automation"])
    options.add_experimental_option("useAutomationExtension", False)
    options.add_argument(
        "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"
    )

    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=options)

    # Ukrycie navigator.webdriver
    driver.execute_cdp_cmd(
        "Page.addScriptToEvaluateOnNewDocument",
        {"source": "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"},
    )

    return driver


def search_kw(driver: webdriver.Chrome, kwNumber=KW_NUMBER_DEF, kwDepartment=KW_DEPARTMENT_DEF,
              kw_checksum_param=0) -> bool:
    print(f"[1] Otwieram stronę: {EKW_URL}")
    driver.get(EKW_URL)

    # Dajemy czas na przejście przez ochronę Incapsula
    print("[*] Czekam na przejście przez ochronę anty-bot...")
    time.sleep(random.randint(3, 11))

    wait = WebDriverWait(driver, 20)

    # Pole: Kod wydziału
    print(f"[2] Wpisuję kod wydziału: {kwDepartment}")
    kod_input = wait.until(
        EC.presence_of_element_located((By.ID, "kodWydzialuInput"))
    )
    kod_input.clear()
    kod_input.send_keys(kwDepartment)

    # Pole: Numer księgi wieczystej
    print(f"[3] Wpisuję numer księgi: {kwNumber}")
    numer_input = wait.until(
        EC.presence_of_element_located((By.ID, "numerKsiegiWieczystej"))
    )
    numer_input.clear()
    numer_input.send_keys(kwNumber)

    # Pole: Cyfra kontrolna (wyliczana automatycznie)
    kwChecksum = str(kw_checksum_param)
    print(f"[4] Wpisuję cyfrę kontrolną: {kwChecksum}")
    cyfra_input = wait.until(
        EC.presence_of_element_located((By.ID, "cyfraKontrolna"))
    )
    cyfra_input.clear()
    cyfra_input.send_keys(kwChecksum)

    # Kliknięcie przycisku "Wyszukaj księgę wieczystą"
    print("[5] Klikam przycisk 'Wyszukaj księgę wieczystą'")
    submit_btn = wait.until(
        EC.element_to_be_clickable((By.ID, "wyszukaj"))
    )
    submit_btn.click()

    # Czekamy na załadowanie wyników
    print("[6] Czekam na wyniki wyszukiwania...")
    time.sleep(6)

    # Weryfikacja wyniku
    page_source = driver.page_source
    title = driver.title
    current_url = driver.current_url

    print(f"[7] Tytuł strony: {title}")
    print(f"    URL: {current_url}")

    if "błąd" in page_source.lower() or "error" in page_source.lower():
        print("[!] Strona może zawierać komunikat o błędzie.")

    if "księga" in page_source.lower() or "LU1I" in page_source:
        print("[OK] Wyszukiwanie zakończone – znaleziono wyniki lub stronę księgi.")
    else:
        print("[?] Nie udało się jednoznacznie potwierdzić wyników.")

    # Zrzut ekranu jako dowód
    screenshot_path = f"../results/ekw_search_result_{kwDepartment}_{kwNumber}_{kwChecksum}.png"
    driver.save_screenshot(screenshot_path)
    print(f"[8] Zrzut ekranu zapisany: {screenshot_path}")

    # Zapis pełnej treści HTML
    if "księga" in page_source.lower() or kwDepartment in page_source:
        html_path = f"../results/ekw_search_result_{kwDepartment}_{kwNumber}_{kwChecksum}.html"
        with open(html_path, "w", encoding="utf-8") as f:
            f.write(page_source)
        print(f"[9] Treść HTML zapisana: {html_path}")

    # Wyjdz jak nie ma ksiegi
    if page_source.find("nie została odnaleziona") != -1:
        print("[!] Księga wieczysta nie została odnaleziona.")
        return False

    # Kliknięcie "Przeglądanie zupełnej treści KW"
    print("[10] Klikam 'Przeglądanie zupełnej treści KW'")
    driver.execute_script("window.scrollBy(0, 100);")
    wait = WebDriverWait(driver, 20)
    btn_view_kw_full = wait.until(
        EC.element_to_be_clickable((By.ID, "przyciskWydrukZupelny"))
    )
    btn_view_kw_full.click()
    time.sleep(3)

    # Zapis okładki
    html_path_kw_cover = f"../results/ekw_search_result_{kwDepartment}_{kwNumber}_okladka.html"
    with open(html_path_kw_cover, "w", encoding="utf-8") as f:
        f.write(driver.page_source)
    print(f"[11] Treść HTML zapisana: {html_path_kw_cover}")

    # Iteracja przez działy KW
    kw_chapters = [
        ("Dział I-O", "I_O"),
        ("Dział I-Sp", "I_Sp"),
        ("Dział II", "II"),
        ("Dział III", "III"),
        ("Dział IV", "IV"),
    ]

    for idx, (kw_chapter_value, kw_chapter_name) in enumerate(kw_chapters, start=12):
        print(f"[{idx}] Klikam '{kw_chapter_value}'")
        btn = wait.until(
            EC.element_to_be_clickable((By.CSS_SELECTOR, f'input[type="submit"][value="{kw_chapter_value}"]'))
        )
        btn.click()
        time.sleep(3)

        result_save_path = f"../results/ekw_search_result_{kwDepartment}_{kwNumber}_{kw_chapter_name}.html"
        with open(result_save_path, "w", encoding="utf-8") as f:
            f.write(driver.page_source)
        print(f"[{idx}] Treść HTML zapisana: {result_save_path}")

    return True


def process_kw(driver: webdriver.Chrome, kwNumber: str, kwDepartment: str, kwChecksum: int) -> None:
    """Wyszukuje księgę wieczystą i zapisuje screenshot oraz HTML."""
    try:
        result = search_kw(driver, kwNumber, kwDepartment, kwChecksum)
        if not result:
            print(f"\n=== Księga {kwDepartment}/{kwNumber} nie została odnaleziona ===")
            return
        print(f"\n=== Wyszukiwanie {kwDepartment}/{kwNumber} zakończone pomyślnie ===")
    except Exception as e:
        print(f"\n[ERROR] {kwDepartment}/{kwNumber}: {e}", file=sys.stderr)
        driver.save_screenshot(f"../results/ekw_search_error_{kwDepartment}_{kwNumber}.png")
        raise


def main():
    headless = "--headless" in sys.argv
    driver = create_driver(headless=headless)
    kwDepartment = KW_DEPARTMENT_DEF
    # startKwNumber = random.randint(1, 100000) # int(KW_NUMBER_DEF)


    conn = init_db()

    try:
        for i in range(100):
            number = random.randint(1, 100000)
            kwNumber = str(number).zfill(8)
            kwChecksum = calculate_kw_checksum(kwDepartment, kwNumber)
            if is_visited(conn, kwNumber):
                continue

            try:
                process_kw(driver, kwNumber, kwDepartment, kwChecksum)
            except Exception as e:
                print(f"[*] Exception '{kwDepartment, kwNumber, kwChecksum}'", e)
                driver.quit()
                driver = create_driver(headless=False)
            # process_kw(driver, "00035932", "LU1I", "4")
    finally:
        driver.quit()


if __name__ == "__main__":
    main()
