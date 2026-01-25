import zipfile
import io

INPUT_FILE = "../../../../../../personal-data.zip"

# 12 * 31 Tage
parties_per_day = [0] * (12 * 31)

with zipfile.ZipFile(INPUT_FILE) as z:
    name = z.namelist()[0]
    with z.open(name) as raw:
        reader = io.BufferedReader(raw)

        reader.readline()  # header überspringen

        for line in reader:
            # line ist bytes, z.B.:
            # b'Greta     ;Weber    ;1953-02-17;\n'

            month = (line[26] - 48) * 10 + (line[27] - 48)
            day   = (line[29] - 48) * 10 + (line[30] - 48)

            idx = (month - 1) * 31 + (day - 1)
            parties_per_day[idx] += 1

# Maximum suchen
max_parties = 0
most_common = None

for i, count in enumerate(parties_per_day):
    if max_parties < count:
        max_parties = count
        most_common = i

if most_common is None:
    print("No birthdays found")
else:
    month = most_common // 31 + 1
    day   = most_common % 31 + 1
    print(
        f"Most common birthday is {month:02d}-{day:02d} "
        f"with {max_parties} persons celebrating it."
    )
