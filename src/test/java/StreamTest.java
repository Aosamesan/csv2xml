import org.junit.Test;

public class StreamTest {
    @Test
    public void shouldConvert() throws Exception {
        String s = CSV2XMLConverterHelper.convertToString("./src/test/test.csv", "./src/test/subItem.csv");
        System.out.println(s);
    }
}
