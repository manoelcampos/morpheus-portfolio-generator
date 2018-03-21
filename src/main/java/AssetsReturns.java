import com.zavtech.morpheus.array.Array;
import com.zavtech.morpheus.frame.DataFrame;
import com.zavtech.morpheus.frame.DataFrameRow;
import com.zavtech.morpheus.frame.DataFrameValue;
import com.zavtech.morpheus.yahoo.YahooFinance;

import java.time.LocalDate;
import java.util.stream.Collectors;

/**
 * Grab daily returns and cumulative returns for a list of assets
 * from local CSV files. In case a file doesn't exist, load the data from Yahoo Finance.
 */
public class AssetsReturns {
    private static final String DAY_RETURNS_FILE = "day-returns-frame";
    private static final String CUM_RETURNS_FILE = "cummulative-returns-frame";
    private final DataFrame<LocalDate, String> dayReturns;
    private final DataFrame<LocalDate, String> cumReturns;

    /**
     * The usual number of days the US stock exchanges work a year.
     * @see <a href="https://en.wikipedia.org/wiki/Trading_day">Tranding days</a>
     */
    public static final int WORK_DAYS_A_YEAR = 252;

    /**
     * Gets stock market data from Yahoo Finance.
     */
    private final YahooFinance yahoo;
    private final Array<String> tickers;

    /**
     * The start date to get data from the specified assets from Yahoo Finance.
     */
    private final LocalDate start;

    /**
     * The end date to get data from the specified assets from Yahoo Finance.
     */
    private final LocalDate end;

    /**
     *
     * @param tickers an Array containing the acronym of assets to get their returns
     */
    public AssetsReturns(final Array<String> tickers, final LocalDate start, final LocalDate end) {
        this.tickers = tickers;
        this.yahoo = new YahooFinance();
        this.start = start;
        this.end = end;

        dayReturns = new DataFrameReaderWriter(getDataFrameFileName(DAY_RETURNS_FILE)).load(this::loadDailyReturnsYahoo);
        cumReturns = new DataFrameReaderWriter(getDataFrameFileName(CUM_RETURNS_FILE)).load(this::loadCumReturnsYahoo);
    }

    private DataFrame<LocalDate, String> loadCumReturnsYahoo() {
        return yahoo.getCumReturns(start, end, getTickers());
    }

    private DataFrame<LocalDate, String> loadDailyReturnsYahoo() {
        return yahoo.getDailyReturns(start, end, getTickers());
    }

    /**
     * Gets a double value and cuts its decimal places so that
     * the number of decimal places will be equal to the param decimalPlaces
     * @param value the value to cut decimal places
     * @param decimalPlaces the number of decimal places to remain after the cut
     * @return the double value with some decimal places cut
     */
    public static double cutDecimalPlaces(final double value, final int decimalPlaces){
        final String fmt = "%."+decimalPlaces+"f";
        return Double.valueOf(String.format(fmt, value));
    }

    public DataFrame<LocalDate, String> getDayReturns() {
        return dayReturns;
    }

    /**
     * Gets the N x N covariance matrix for all assets defined in {@link #tickers}
     * for all working days (trading days).
     * @return
     */
    public DataFrame<String, String> covarianceMatrix(){
        return dayReturns.cols().stats().covariance().applyDoubles(v -> v.getDouble() * WORK_DAYS_A_YEAR);
    }

    /**
     * Gets the cumulative returns for all assets for the entire period.
     * @return a DataFrame containing a row for every asset, containing the total cumulative return
     *         for that asset
     */
    public DataFrame<LocalDate, String> getTotalCumulativeReturns(){
        return cumReturns.rows().last().map(DataFrameRow::toDataFrame).get();
    }

    public DataFrame<LocalDate, String> getCumReturns() {
        return cumReturns;
    }

    private String getDataFrameFileName(final String baseName) {
        return baseName + "_" + assetsArrayToString(getTickers()) + ".csv";
    }

    private String assetsArrayToString(final Array<String> assets) {
        return assets.toList().stream().collect(Collectors.joining(", "));
    }

    /**
     * The list of assets acronyms for which load data.
     */
    public Array<String> getTickers() {
        return tickers;
    }
}
