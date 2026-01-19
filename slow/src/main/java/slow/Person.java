package slow;

import com.opencsv.bean.CsvBindByPosition;
import com.opencsv.bean.CsvDate;
import lombok.Data;

import java.time.LocalDate;

@Data
public class Person {

    @CsvBindByPosition(position = 0)
    public String firstName;

    @CsvBindByPosition(position = 1)
    public String lastName;

    @CsvBindByPosition(position = 2)
    @CsvDate(value = "yyyy-MM-dd")
    public LocalDate birthDate;
}
