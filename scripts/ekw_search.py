"""
Serwis do automatycznego wyszukiwania ksiąg wieczystych
w portalu https://przegladarka-ekw.ms.gov.pl

Wyszukuje księgę wieczystą po numerze: LU1I/00016057/7

Używa Selenium z Chrome WebDriver i opcjami anti-detection,
aby ominąć ochronę Incapsula/Imperva.
"""

import time
import sys

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

KOD_WYDZIALU = "LU1I"
NUMER_KSIEGI = "00016057"


def calculate_check_digit(kod_wydzialu: str, numer_ksiegi: str) -> int:
    """
    Wylicza cyfrę kontrolną księgi wieczystej.

    Algorytm:
    - Łączy kod wydziału (4 znaki) z numerem księgi (8 cyfr) w 12-znakowy ciąg.
    - Każdy znak mnożony jest przez odpowiednią wagę z cyklu [1, 3, 7].
    - Litery zamieniane są na wartości 1-9 (pozycja w alfabecie mod 9, z mapowaniem 0->9).
    - Cyfra kontrolna to suma ważona mod 10.
    """
    input_str = (kod_wydzialu + numer_ksiegi).upper()
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


def search_ksiega(driver: webdriver.Chrome) -> None:
    print(f"[1] Otwieram stronę: {EKW_URL}")
    driver.get(EKW_URL)

    # Dajemy czas na przejście przez ochronę Incapsula
    print("[*] Czekam na przejście przez ochronę anty-bot...")
    time.sleep(7)

    wait = WebDriverWait(driver, 20)

    # Pole: Kod wydziału
    print(f"[2] Wpisuję kod wydziału: {KOD_WYDZIALU}")
    kod_input = wait.until(
        EC.presence_of_element_located((By.ID, "kodWydzialuInput"))
    )
    kod_input.clear()
    kod_input.send_keys(KOD_WYDZIALU)

    # Pole: Numer księgi wieczystej
    print(f"[3] Wpisuję numer księgi: {NUMER_KSIEGI}")
    numer_input = wait.until(
        EC.presence_of_element_located((By.ID, "numerKsiegiWieczystej"))
    )
    numer_input.clear()
    numer_input.send_keys(NUMER_KSIEGI)

    # Pole: Cyfra kontrolna (wyliczana automatycznie)
    cyfra_kontrolna = str(calculate_check_digit(KOD_WYDZIALU, NUMER_KSIEGI))
    print(f"[4] Wpisuję cyfrę kontrolną: {cyfra_kontrolna}")
    cyfra_input = wait.until(
        EC.presence_of_element_located((By.ID, "cyfraKontrolna"))
    )
    cyfra_input.clear()
    cyfra_input.send_keys(cyfra_kontrolna)

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
    screenshot_path = "ekw_search_result.png"
    driver.save_screenshot(screenshot_path)
    print(f"[8] Zrzut ekranu zapisany: {screenshot_path}")


def main():
    headless = "--headless" in sys.argv
    driver = create_driver(headless=headless)
    try:
        search_ksiega(driver)
        print("\n=== Wyszukiwanie zakończone pomyślnie ===")
    except Exception as e:
        print(f"\n[BŁĄD] {e}", file=sys.stderr)
        driver.save_screenshot("ekw_search_error.png")
        raise
    finally:
        driver.quit()


if __name__ == "__main__":
    main()
