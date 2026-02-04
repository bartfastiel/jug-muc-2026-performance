# jug-muc-2026-performance
bartfastiel's presentation for Java User Group Munich about performance optimization

## task

Given an input file with personal data records, find the day of the year, where most people celebrate their birthday.

### input file format

Comma-separated text file with fixed width columns. Lines separated by `\n`. Columns separated by `;`.
No escaping nor quoting allowed. Dates are in 'YYYY-MM-DD' format. First line is a header.

The input textfile is zipped.

example:
```
First name;Last name       ;Birth date;
Hans      ;Fischer         ;1964-03-12;
Susi      ;Fischer         ;1959-08-06;
Hans      ;Schmidt         ;1985-09-13;
```

### scripts

```
time /C/jug-performance/graalvm-jdk-25.0.2+10.1/bin/java.exe slow/src/main/java/solution/kilo/Main.java
time /C/jug-performance/jdk-25_windows-x64_bin/jdk-25.0.2/bin/java.exe slow/src/main/java/solution/kilo/Main.java
```
