import com.zavtech.morpheus.frame.DataFrame;
import com.zavtech.morpheus.sink.CsvSinkOptions;
import com.zavtech.morpheus.util.text.Formats;
import com.zavtech.morpheus.util.text.printer.Printer;
import com.zavtech.morpheus.yahoo.YahooFinance;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * Enables to read/write a DataFrame from/to a CSV file.
 * If a file to be read is not found, allows
 * fetching the DataFrame from any source,
 * such as the {@link YahooFinance},
 * using a {@link Supplier} function that encapsulates
 * how the data has to be fetched.
 *
 * <p><b>Credits:</b> Class based on the code available at
 * the <a href="http://www.zavtech.com/morpheus/docs/examples/mpt/#multiple-assets">documentation page</a>
 * for the <a href="https://github.com/zavtech">ZavTech Morpheus Library</a>
 * by <a href="https://github.com/Zavster">Xavier Witdouck</a>.</p>
 *
 * @author Manoel Campos da Silva Filho
 */
public class DataFrameReaderWriter {
    private final String fileName;
    private String keyColumnTitle;

    /**
     * Creates an instance to manipulate a DataFrame CSV file.
     * @param fileName the name of the CSV file
     */
    public DataFrameReaderWriter(final String fileName){
        this.fileName = fileName;
    }

    public void save(final DataFrame<LocalDate, String> dataFrame){
        if(dataFrame != null) {
            dataFrame.write().csv(this::setCsvOptions);
        }
    }

    /**
     * Checks if a given DataFrame file exists and then loads it.
     * Otherwise, fetch the data using a {@link Supplier}.
     * Such Supplier can get, for instance, data from the Yahoo Finance
     * or any other kind of datasource.
     * If the DataFrame is loaded from the Supplier,
     * then it is saved to the given file name, so that
     * the next time it can be read locally.
     *
     * @param dataFrameSupplier the {@link Supplier} to get the data from
     *                          an external DataSource (such as {@link YahooFinance})
     *                          when the DataFrame file doesn't exist
     * @return the loaded DataFrame from the File or got from the given {@link Supplier}
     */
    public DataFrame<LocalDate, String> load(final Supplier<DataFrame<LocalDate, String>> dataFrameSupplier)
    {
        if(new File(fileName).exists()) {
            System.out.println("Loaded DataFrame from file " + fileName);
            return DataFrame.<LocalDate, String>read()
                    .csv(options -> {
                        options.setFile(new File(fileName));
                        //Avoids the key column to be duplicated when creating the DataFrame from a file
                        options.setExcludeColumnIndexes(0);
                        options.setRowKeyParser(LocalDate.class, row -> LocalDate.parse(row[0]));
                    });
        }

        final DataFrame<LocalDate, String> dataFrame = dataFrameSupplier.get();
        save(dataFrame);
        return dataFrame;
    }

    private void setCsvOptions(final CsvSinkOptions<LocalDate> options) {
        options.setFile(fileName);
        options.setSeparator(",");
        options.setIncludeRowHeader(true);
        options.setIncludeColumnHeader(true);
        options.setNullText("null");
        if (keyColumnTitle != null) {
            options.setTitle(keyColumnTitle);
        }

        options.setFormats(this::format);
    }

    /**
     * Defines the format for printing data into the CSV file,
     * for specific fields of data types.
     * @param formats the object that stores all defined formats
     */
    private void format(final Formats formats) {
        formats.setDecimalFormat(Double.class, "0.00##;-0.00##", 1);
    }

    public DataFrameReaderWriter setKeyColumnTitle(final String keyColumnTitle) {
        this.keyColumnTitle = keyColumnTitle;
        return this;
    }
}
