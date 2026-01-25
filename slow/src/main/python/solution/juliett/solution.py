import zipfile
import numpy as np
import pandas as pd

with zipfile.ZipFile("../../../../../../personal-data.zip") as z:
    name = z.namelist()[0]
    data = z.read(name)  # bytes

# Header bis erstes \n überspringen
nl = data.find(b"\n")
if nl < 0:
    raise RuntimeError("No newline found (expected \\n-separated lines).")

body = data[nl + 1 :]

LINE_LEN = 33  # inkl. \n (wie in deinem Java-Indexing impliziert)

# auf volle Zeilen schneiden (falls am Ende noch was "hängt")
rem = len(body) % LINE_LEN
if rem != 0:
    body = body[: len(body) - rem]

if len(body) == 0:
    print("No birthdays found")
else:
    rows = np.frombuffer(body, dtype=np.uint8).reshape(-1, LINE_LEN)

    # MM-DD als 5 Bytes bauen: [M1, M2, '-', D1, D2]
    out = np.empty((rows.shape[0], 5), dtype=np.uint8)
    out[:, 0] = rows[:, 26]
    out[:, 1] = rows[:, 27]
    out[:, 2] = 45  # ord('-')
    out[:, 3] = rows[:, 29]
    out[:, 4] = rows[:, 30]

    mmdd = out.view("S5").reshape(-1).astype("U5")

    counts = pd.Series(mmdd).value_counts()
    day = counts.idxmax()
    num = int(counts.max())

    print(f"Most common birthday is {day} with {num} persons celebrating it.")
