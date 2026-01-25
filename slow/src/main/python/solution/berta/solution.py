import zipfile
import pandas as pd

# ZIP-Datei öffnen
with zipfile.ZipFile("../../../../../../personal-data.zip") as z:
    csv_name = z.namelist()[0]  # erste (und einzige) Datei
    df = pd.read_csv(
        z.open(csv_name),
        sep=";",
        usecols=["Birth date"]
    )

# Datum parsen
dates = pd.to_datetime(df["Birth date"], format="%Y-%m-%d")

# Nur Tag im Jahr betrachten (MM-DD)
day_counts = dates.dt.strftime("%m-%d").value_counts()

# Häufigster Geburtstag
most_common_day = day_counts.idxmax()
count = day_counts.max()

print(f"Häufigster Geburtstag: {most_common_day} ({count} Personen)")
