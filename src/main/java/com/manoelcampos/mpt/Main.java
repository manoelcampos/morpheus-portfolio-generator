package com.manoelcampos.mpt;

import com.zavtech.morpheus.array.Array;
import com.zavtech.morpheus.frame.DataFrame;
import com.zavtech.morpheus.frame.DataFrameRow;
import com.zavtech.morpheus.frame.DataFrameRows;
import com.zavtech.morpheus.frame.DataFrameValue;
import com.zavtech.morpheus.range.Range;
import com.zavtech.morpheus.util.Tuple;
import com.zavtech.morpheus.viz.chart.Chart;
import com.zavtech.morpheus.viz.chart.xy.XyPlot;
import com.zavtech.morpheus.yahoo.YahooFinance;

import java.io.File;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.function.Supplier;

/**
 * Console application that applies the Modern Portfolio Theory (MPT)
 * to plot randomly generated portfolios with different weights
 * for different groups of assets (stocks). It defines groups of assets than randomly generate N
 * portfolios with different shares (weights) of such assets, for every defined group.
 *
 * <p>Each group is plotted with a different color, so that one can evaluate the risk/return trade-off
 * achieved by the diversification effect for different number of assets and different shares
 * of such assets.</p>
 *
 * <p>The application extract stock market data from the <a href="http://finance.yahoo.com">Yahoo Finance WebSite</a>,
 * using the <a href="http://www.zavtech.com/morpheus/docs/providers/yahoo/">morpheus-yahoo unofficial API</a>.</p>
 *
 * <p><b>Credits:</b> Class based on the code available at
 * the <a href="http://www.zavtech.com/morpheus/docs/examples/mpt/#multiple-assets">documentation page</a>
 * for the <a href="https://github.com/zavtech">ZavTech Morpheus Library</a>
 * by <a href="https://github.com/Zavster">Xavier Witdouck</a>.</p>
 *
 * @author Manoel Campos da Silva Filho
 */
public class Main implements Runnable {
    /**
     * Number of random portfolios to generate for each {@link #tickersArray group of assets.}
     */
    private static final int COUNT = 100_000;
    private static final String SUBTITLE = COUNT + " Randomly Generated Portfolio Combinations";
    private static final String X_AXIS_LABEL = "Portfolio Risk";
    private static final String Y_AXIS_LABEL = "Portfolio Return";
    private static final int RISK_COL = 0;
    private static final int RETURN_COL = 1;
    private final DataFrame<Integer, String> efficientFrontierPortfolios;
    private String title = "Risk/Return Portfolios";
    private final DataFrame<Integer,String> emptyDataFrame = getEmptyRiskReturnDataFrame();
    private Chart<XyPlot<Integer>> chart;

    /** Defines the end of the investment horizon */
    private final LocalDate end = LocalDate.now().minusDays(1);

    /** Defines the beginning of the investment horizon.
     * Yahoo doesn't return values for a large interval (more than 4 months). */
    private final LocalDate start = end.minusMonths(4);

    private final Comparator<DataFrameRow<Integer, String>> riskComparator = Comparator.comparingDouble(row -> row.getValue(RISK_COL));
    private final Comparator<DataFrameRow<Integer, String>> returnComparator = Comparator.comparingDouble(row -> row.getValue(RETURN_COL));

    /**
     * An Array with the ticker of assets to create portfolios.
     * https://query1.finance.yahoo.com/v8/finance/chart/MGLU3.SA?period1=1546311600&period2=1556593200&interval=1d&includePrePost=False
     */
    private final Array<String> tickersArray = Array.of(
        "MGLU3.sa", "PETR4.sa", "SQIA3.sa", "BIDI3.sa", "BBAS3.sa", "ITSA3.sa",
        "SUZB3.sa", "BBDC3.sa", "VALE3.sa", "LAME3.sa", "VVAR3.sa", "WEGE3.sa"
    );

    /**
     * A {@link DataFrame} containing the overall risk and returns for every randomly generated portfolio,
     * for every asset defined in {@link #tickersArray}.
     * The DataFrame represents all the portfolios for those assets.
     * Each row represents a portfolio where the fist column is the overall risk and
     * the second the return.
     * @see #computeRiskAndReturnForPortfolios()
     */
    private final DataFrame<Integer,String> portfolios;

    public static void main(String[] args) {
        new Main();
    }

    private Main(){
        portfolios = computeRiskAndReturnForPortfolios();
        this.efficientFrontierPortfolios = getEfficientFrontierPortfolios();
        System.out.println();
        plot();
    }

    private DataFrame<Integer, String> getEmptyRiskReturnDataFrame() {
        return DataFrame.ofDoubles(0, Array.of(String.class, "Risk", "Return"));
    }

    /**
     * The efficient frontier is the line starting at the tip of the bullet-shaped portfolios curve,
     * that splits the curve in a lower and upper half. This tip is the most efficient portfolio, which has
     * the lower risk and the higher return for that level of risk.
     * The efficient frontier is so drawn from all points along the risk axis (X) that corresponds to the
     * maximum return (Y axis) that can be got for that level of risk.
     */
    private DataFrame<Integer, String> getEfficientFrontierPortfolios() {
        final var efficientPortfolio = getMostEfficientPortfolio();
        final double efficientPortfolioReturn = efficientPortfolio.rowAt(0).getDouble(RETURN_COL);
        System.out.println("Efficient Portfolio Return: " + efficientPortfolioReturn);

        /*
         * Gets all the portfolios belonging to the upper half of the bullet-shaped portfolios curve.
         * Those are the ones for which you can get higher returns for a given level of risk,
         * compared to portfolios at the same risk level but belonging to the lower half of the graph.
         */
        final DataFrameRows<Integer, String> upperHalfPortfolios = portfolios
                .rows()
                .filter(row -> row.getDouble(RETURN_COL) >= efficientPortfolioReturn);

        /*Gets the portfolios having the max return for a given risk.
        Since the precision of the risk value is too high, cuts the number of decimal places
        for the risk values to 1 to ensure correct grouping.
        With all the original decimal places, hardly ever a risk value repeats
        so that it could be grouped.
        * */
        final DataFrame<Integer, String> df = upperHalfPortfolios
                .groupBy(row -> Tuple.of(AssetsReturns.cutDecimalPlaces(row.getDouble("Risk"), 2)))
                .stats(0)
                .max()
                .rows()
                //redefines row keys as their indexes
                .mapKeys(DataFrameRow::ordinal);

        System.out.println(
                "Portfolios at upper half of the curve: " + upperHalfPortfolios.count() +
                " Max return portfolios: " + df.rowCount());

        /* Redefine the name of the return column to be different from the
         * dataframe from which the efficient frontier portfolios were gotten. */
        df.cols().replaceKey(df.colAt(1).key(), "Efficient Frontier");
        //df.out().print(df.rowCount());
        return df;
    }

    /**
     * Gets the most efficient portfolio for the last group of assets.
     * That is, the portfolio with the lower risk and higher return.
     * Such a portfolio defines the point on the tip of the bullet-shaped portfolios curve
     * that divides the lower and upper half of the curve.
     * All the portfolios on the border of the upper half of the curve are in the efficient frontier.
     *
     * @return the most efficient portfolio from the list of portfolios given
     */
    private DataFrame<Integer, String> getMostEfficientPortfolio() {
        final var row = portfolios
                            .rows()
                            .min(riskComparator.thenComparing(returnComparator.reversed()))
                            .orElseGet(() -> emptyDataFrame.rowAt(0));

        return createDataFrameFromRow(row);
    }

    /**
     * Creates a DataFrame from a given row representing the most efficient portfolio
     * for a group of assets (that is, the portfolio with lower risk and the higher
     * return for that level of risk).
     *
     * The row is added to the new DataFrame using the same key (the index of the row).
     * An additional row is added to the extreme right of the risk axis (X), so that
     * a line splitting the lower and upper half of the bullet-shaped portfolios chart
     * can be drawn.
     *
     * @param row the row to add its values to the single row included in the DataFrame
     * @return a {@code DataFrame<Integer, String>} created from the given row
     * @see #configureChart(Chart)
     */
    private DataFrame<Integer, String> createDataFrameFromRow(final DataFrameRow<Integer, String> row) {
        final var df = DataFrame.ofDoubles(
                                    Array.of(row.key(), row.key()+1),
                                    Array.of(String.class, "Risk", "Division between efficient and inefficient portfolios"));

        /* Defines the most efficient portfolio,
         * a point represented by a row having the lower risk and higher return
         * for this level of risk.
        */
        df.rowAt(0).setValue(RISK_COL, row.getValue(RISK_COL));
        df.rowAt(0).setValue(RETURN_COL, row.getValue(RETURN_COL));

        /* Creates another portfolio with the same return but the higher risk (at the extreme right of the X axis
        and at the same position in Y). This way, a straight to split the lower and upper half
        of the bullet-shaped portfolios curve can be draw.
        @todo This 13.5 value is the higher risk but it has to be computed automatically
        */
        df.rowAt(1).setValue(RISK_COL, 13.5);
        df.rowAt(1).setValue(RETURN_COL, row.getValue(RETURN_COL));
        return df;
    }

    /**
     * Computes the risk and return for {@link #COUNT} random generated portfolios compounded of a given
     * list of assets.
     *
     * @return a {@link DataFrame} representing all generated portfolios,
     *         containing the risk and return, from the given assets' group.
     *         Each row in this DataFrame represents a portfolio where the rows are
     *         indexed by the portfolio number (Integer).
     *         The first column is the portfolio return and the second one is the portfolio risk (variance).
     *         The Strings that index the columns are used to label them accordingly.
     */
    private DataFrame<Integer, String> computeRiskAndReturnForPortfolios() {
        final var yahoo = new YahooFinance();
        final Supplier<DataFrame<LocalDate, String>> dailyReturnsSupplier = () -> yahoo.getDailyReturns(start, end, tickersArray);
        final Supplier<DataFrame<LocalDate, String>> cumulReturnsSupplier = () -> yahoo.getCumReturns(start, end, tickersArray);
        final var returns = new AssetsReturns(tickersArray, dailyReturnsSupplier, cumulReturnsSupplier);

        //Generate random portfolios and compute risk & return for each
        final DataFrame<Integer, String> portfolios = createRandomPortfolios(COUNT, tickersArray);
        final var label = String.format("%s Assets Portfolios", tickersArray.length());

        /*
        A DataFrame to be filled with the overall risk and returns for every generated portfolio of the group.
        Each row is a portfolio (indexed by its Integer number).
        The values for each row are DataFrameValue objects, each one
        having columns: the 1st is the return and the 2nd the risk.
        */
        final DataFrame<Integer, String> portfoliosRiskReturn =
                DataFrame.ofDoubles(Range.of(0, COUNT), Array.of("Risk", label));

        for (final DataFrameRow<Integer, String> p: portfolios.rows()) {
            computePortfolioRiskAndReturn(p, returns, portfoliosRiskReturn);
        }

        return portfoliosRiskReturn;
    }

    /**
     * Computes the overall risk and return for a given portfolio.
     *
     * @param portfolio a row representing a portfolio where the row is
     *         indexed by the portfolio number (Integer) and the columns are named using the assets names (String).
     *         Each value in the row is a Stream of {@link DataFrameValue} where
     *         the columns are named using the asset name (DataFrameValue colKey)
     *         and each column value is the risk for an asset.
     * @param returns the returns of all assets in this portfolio
     * @param assetsRisksReturn a DataFrame to be <b>filled</b> with the overall risk and return of the portfolio.
     *                          Each row in this DataFrame is a portfolio. The first column is the portfolio
     *                          return and the second one is the portfolio risk (variance).
     *                          The Strings that index the columns are used to label them accordingly.
     */
    private void computePortfolioRiskAndReturn(
            final DataFrameRow<Integer, String> portfolio,
            final AssetsReturns returns,
            final DataFrame<Integer, String> assetsRisksReturn)
    {
        final DataFrame<Integer, String> weights = portfolio.toDataFrame();

        final double portfolioReturn = computePortfolioReturn(returns, weights);
        final double portfolioVariance = computePortfolioRisk(returns, weights);

        assetsRisksReturn.data().setDouble(portfolio.key(), RISK_COL, Math.sqrt(portfolioVariance) * 100d);
        assetsRisksReturn.data().setDouble(portfolio.key(), RETURN_COL, portfolioReturn * 100d);
    }

    /**
     * Computes the overall portfolio risk, based on the weight and return of each asset.
     *
     * @param returns the return of all assets in the portfolio
     * @param weights the weights for each asset in the portfolio
     * @return the overall portfolio risk.
     */
    private double computePortfolioRisk(final AssetsReturns returns, final DataFrame<Integer, String> weights) {
        /* Since the dot product operation between two arrays/matrices is a single scalar value,
         * the value from row 0 and column 0 is being got from the resulting DataFrame. */
        return weights.dot(returns.covarianceMatrix()).dot(weights.transpose()).data().getDouble(0, 0);
    }

    /**
     * Computes the overall portfolio return, based on the weight and return of each asset.
     *
     * @param returns the returns of all assets in the portfolio
     * @param weights the weights for each asset in the portfolio
     * @return the overall portfolio return.
     */
    private double computePortfolioReturn(final AssetsReturns returns, final DataFrame<Integer, String> weights) {
        /* Since the dot product operation between two arrays/matrices is a single scalar value,
        *  the value from the row 0 and column 0 is being got from the resulting DataFrame. */
        return weights.dot(returns.getTotalCumulativeReturns().transpose()).data().getDouble(0, 0);
    }

    private void plot() {
        //Chart.create().htmlMode();  //Globally enables HTML mode (it doesn't create a chart in fact)
        chart = Chart.create()
                     .withScatterPlot(portfolios, false, "Risk", this::configureChart);

        //new Thread(this).start();
        run();
    }

    /**
     * Exports the chart to a graph.
     */
    @Override
    public void run() {
        try { Thread.sleep(10000); } catch (InterruptedException e) {/**/}
        chart.writerPng(new File("portfolios-analysis-"+COUNT+"-assets.png"), 800, 600, true);
    }

    private void configureChart(final Chart<XyPlot<Integer>> chart) {
        final int dotsDiameter = 4;
        chart.plot().render(0).withDots(dotsDiameter);

        //Inserts the portfolios from the efficient frontier.
        chart.plot().<String>data().add(efficientFrontierPortfolios, "Risk");
        chart.plot().render(1).withDots(dotsDiameter);

        chart.plot().axes().domain().label().withText(X_AXIS_LABEL);
        chart.plot().axes().domain().format().withPattern("0.0'%';-0.0'%'");
        chart.plot().axes().range(0).label().withText(Y_AXIS_LABEL);
        chart.plot().axes().range(0).format().withPattern("0.0'%';-0.0'%'");
        chart.title().withText(title);
        chart.subtitle().withText(SUBTITLE);
        chart.legend().on().bottom();
        chart.show();
    }

    /**
     * Generates N long only random portfolios with weights that add up to 1,
     * all of them containing a given list of assets.
     *
     * @param n         the number of portfolios (rows) in the DataFrame
     * @param assets    the assets to include for the generated portfolios (a list of the asset's acronyms)
     * @return          a DataFrame containing N random generated portfolios (1 per row).
     *                  Each DataFrameRow contains columns named with the asset acronym,
     *                  which the value is a double number representing the weight for each asset
     */
    private DataFrame<Integer,String> createRandomPortfolios(final int n, final Iterable<String> assets) {
        final DataFrame<Integer,String> weights = DataFrame.ofDoubles(Range.of(0, n), assets);

        //Generates random weights (percentages from 0 to 1) for each asset (column) in the DataFrameRow
        weights.applyDoubles(frameValue -> Math.random());

        weights.rows().forEach(this::normalizePortfolioWeights);
        return weights;
    }

    /**
     * Normalizes the weights for each asset in the portfolio from the data contained in a {@link DataFrameRow}.
     * Each DataFrameRow contains columns named with the asset acronym,
     * which the value is a double number representing the weight for each asset.
     * Since the sum of the randomly generated weights can be greater than 1 (100%),
     * this function normalizes it so that all weights add up to 1.
     *
     * @param portfolio the {@link DataFrameRow} representing the portfolio to normalize the weights
     */
    private void normalizePortfolioWeights(final DataFrameRow<Integer, String> portfolio) {
        //The sum of all weights for each asset contained in the DataFrameRow representing the portfolio
        final double totalWeights = portfolio.stats().sum();

        //v.getDouble() is the weight for the asset (column) in the portfolio (DataFrameRow)
        portfolio.applyDoubles(v -> v.getDouble() / totalWeights);
    }

}
