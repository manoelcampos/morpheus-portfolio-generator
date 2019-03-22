package com.manoelcampos.mpt;

import com.zavtech.morpheus.array.Array;
import com.zavtech.morpheus.frame.DataFrame;
import com.zavtech.morpheus.frame.DataFrameRow;
import com.zavtech.morpheus.yahoo.YahooFinance;

import java.time.LocalDate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Grab daily and cumulative returns for a list of assets from local CSV files.
 * In case a file doesn't exist, fetch the data from any external source,
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
public class AssetsReturns {
    private static final String DAILY_RETURNS_FILE = "day-returns-frame";
    private static final String CUMUL_RETURNS_FILE = "cumulative-returns-frame";

    /** @see #getDailyReturns() */
    private final DataFrame<LocalDate, String> dailyReturns;
    /** @see #getCumulReturns() */
    private final DataFrame<LocalDate, String> cumulReturns;

    /**
     * The usual number of days the US stock exchanges work a year.
     * @see <a href="https://en.wikipedia.org/wiki/Trading_day">Trading days</a>
     */
    public static final int WORK_DAYS_A_YEAR = 252;

    private final Array<String> tickers;

    /**
     * Instantiates an AssertsReturns.
     *
     * @param tickers an {@link Array} containing the acronym of assets to get their returns
     * @param dailyReturnsSupplier a {@link Supplier} that may be used to load assets daily returns
     *                             from an external source when the data file is not found locally
     * @param cumulReturnsSupplier a {@link Supplier} that may be used to load assets cumulative returns
     *                             from an external source when the data file is not found locally
     */
    public AssetsReturns(
        final Array<String> tickers,
        final Supplier<DataFrame<LocalDate, String>> dailyReturnsSupplier,
        final Supplier<DataFrame<LocalDate, String>> cumulReturnsSupplier)
    {
        this.tickers = tickers;
        dailyReturns = new DataFrameReaderWriter(dataFrameFileName(DAILY_RETURNS_FILE)).load(dailyReturnsSupplier);
        cumulReturns = new DataFrameReaderWriter(dataFrameFileName(CUMUL_RETURNS_FILE)).load(cumulReturnsSupplier);
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

    /**
     * The list of assets acronyms for which data will be loaded.
     */
    public Array<String> getTickers() {
        return tickers;
    }

    /**
     * Gets a {@link DataFrame} with the assets' daily returns.
     * @return
     * @see #getTickers()
     */
    public DataFrame<LocalDate, String> getDailyReturns() {
        return dailyReturns;
    }

    /**
     * Gets a {@link DataFrame} with the assets' cumulative returns, i.e.,
     * each row in such a DataFrame is the sum of the returns from previous
     * days, since the first date in the interval where the data is contained.
     * This way, the last row represents the total cumulative returns for that period.
     *
     * @return
     * @see #getTickers()
     */
    public DataFrame<LocalDate, String> getCumulReturns() {
        return cumulReturns;
    }

    /**
     * Gets a {@link DataFrame} containing a single row with
     * the total cumulative returns of every asset (as columns) for the entire period.
     *
     * @return a DataFrame with a single row containing the cumulative returns for every asset
     * @see #getCumulReturns()
     */
    public DataFrame<LocalDate, String> getTotalCumulativeReturns(){
        return cumulReturns.rows().last().map(DataFrameRow::toDataFrame).orElseGet(DataFrame::empty);
    }

    /**
     * Gets the N x N covariance matrix for all assets defined in {@link #tickers}
     * for all working days (trading days).
     * @return
     */
    public DataFrame<String, String> covarianceMatrix(){
        return dailyReturns.cols().stats().covariance().applyDoubles(v -> v.getDouble() * WORK_DAYS_A_YEAR);
    }

    private String dataFrameFileName(final String baseName) {
        return baseName + "_" + assetsArrayToString(getTickers()) + ".csv";
    }

    private String assetsArrayToString(final Array<String> assets) {
        return assets.stream().values().collect(Collectors.joining(", "));
    }
}
