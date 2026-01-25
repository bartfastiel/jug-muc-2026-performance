import zipfile
import pandas as pd

with zipfile.ZipFile("../../../../../../personal-data.zip") as z:
    csv_name = z.namelist()[0]
    df = pd.read_csv(
        z.open(csv_name),
        sep=";",
        usecols=["Birth date"]
    )

# nur "MM-DD" aus "YYYY-MM-DD"
mm_dd = df["Birth date"].str[-5:]

counts = mm_dd.value_counts()

most_common_day = counts.idxmax()
count = counts.max()

print(f"Häufigster Geburtstag: {most_common_day} ({count} Personen)")
