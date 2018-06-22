import com.zavtech.morpheus.array.Array;
import com.zavtech.morpheus.array.ArrayValue;
import com.zavtech.morpheus.frame.*;
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
public class PortfolioGenerationApp {
    /**
     * Number of random portfolios to generate for each {@link #assetsGroups group of assets.}
     */
    private static final int COUNT = 100000;
    private static final String SUBTITLE = COUNT + " Randomly Generated Portfolio Combinations";
    private static final String X_AXIS_LABEL = "Portfolio Risk";
    private static final String Y_AXIS_LABEL = "Portfolio Return";
    private static final int RISK_COL = 0;
    private static final int RETURN_COL = 1;
    private final DataFrame<Integer, String> efficientPortfolio;
    private String title = "Risk/Return Portfolios";
    private final DataFrame<Integer,String> emptyDataFrame = getEmptyRiskReturnDataFrame();

    private DataFrame<Integer, String> getEmptyRiskReturnDataFrame() {
        return DataFrame.ofDoubles(0, Array.of(String.class, "Risk", "Return"));
    }

    //Define investment horizon
    private final LocalDate end = LocalDate.of(2018, 03, 17);
    private final LocalDate start = end.minusYears(1);

    private static final Comparator<DataFrameRow<Integer, String>> riskComparator = Comparator.comparingDouble(row -> row.getValue(RISK_COL));
    private static final Comparator<DataFrameRow<Integer, String>> returnComparator = Comparator.comparingDouble(row -> row.getValue(RETURN_COL));

    /**
     * An Array containing internal arrays representing groups of assets
     * to be used to create portfolios using only the assets in the group.
     * Each group represents the assets to be used
     * to generate {@link #COUNT} portfolios, each one with different random weights for the containing
     * assetsGroups.
     *
     * One can add internal Arrays with different number of assets to show the
     * generated portfolios from these assets behave in terms of risk and return,
     * as the number of assets increases.
     */
    private final Array<Array<String>> assetsGroups = Array.of(
            Array.of("VWO", "VNQ", "VEA")
            //, Array.of("VNQ", "VEA")
    );

    /**
     * An array of {@link DataFrame} containing the overall risk and returns for every randomly generated portfolio,
     * for every group given by the {@link #assetsGroups}.
     * Each DataFrame represents all the portfolios for a group of assets.
     * Each row represents a portfolio where the fist column is the overall risk and
     * the second the return.
     *
     * @see #computeRiskAndReturnForPortfolios(ArrayValue)
     */
    private Array<DataFrame<Integer,String>> portfoliosByGroup;

    public static void main(String[] args) {
        new PortfolioGenerationApp();
    }

    private PortfolioGenerationApp(){
        if(assetsGroups.length() > 1) {
            title += " with Increasing Number of Assets";
        }

        portfoliosByGroup = assetsGroups.map(this::computeRiskAndReturnForPortfolios);

        this.efficientPortfolio =
                getMostEfficientPortfolio(portfoliosByGroup.getValue(assetsGroups.length()-1));

        final DataFrame<Integer, String> efficientFrontierPortfolios = getEfficientFrontierPortfolios();

        /*
         * Adds the most efficient portfolio for every assets' group to the groups of created portfolios.
         * This way, the dot corresponding to the most efficient portfolios are highlighted in the chart.
         * Array.concat is throwing IndexOutOfBoundsException
         */
        portfoliosByGroup.expand(portfoliosByGroup.length()+2);
        portfoliosByGroup.setValue(portfoliosByGroup.length()-2, efficientFrontierPortfolios);
        portfoliosByGroup.setValue(portfoliosByGroup.length()-1, efficientPortfolio);

        //portfoliosByGroup.forEach(p -> p.out().print());

        System.out.println();
        final DataFrameRow<Integer, String> row = efficientPortfolio.rowAt(0);
        System.out.printf(
            "\nMost Efficient Portfolio %d: Risk %.2f Return %.2f\n",
            row.key(),
            row.getValue(RISK_COL),
            row.getValue(RETURN_COL));

        plot();
    }

    /**
     * The efficient frontier is the line starting at the tip of the bullet-shaped portfolios curve,
     * that splits the curve in a lower and upper half. This tip is the most efficient portfolio, which has
     * the lower risk and the higher return for that level of risk.
     * The efficient frontier is so drawn from all points along the risk axis (X) that corresponds to the
     * maximum return (Y axis) that can be got for that level of risk.
     */
    private DataFrame<Integer, String> getEfficientFrontierPortfolios() {
        final double efficientPortfolioReturn = efficientPortfolio.rowAt(0).getDouble(RETURN_COL);
        System.out.println("Efficient Portfolio Return: " + efficientPortfolioReturn);

        /*
         * Gets all the portfolios belonging to the upper half of the bullet-shaped portfolios curve.
         * Those are the ones for which you can get higher returns for a given level of risk,
         * compared to portfolios at the same risk level but belonging to the lower half of the graph.
         */
        final DataFrameRows<Integer, String> upperHalfPortfolios = portfoliosByGroup
                .getValue(assetsGroups.length() - 1)
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
     * @param portfolios the DataFrame containing the portfolios to get the most efficient one,
     *                   where each row is a portfolio containing the risk at the first column
     *                   and the return at the second column
     *
     * @return the most efficient portfolio from the list of portfolios given
     */
    private DataFrame<Integer, String> getMostEfficientPortfolio(DataFrame<Integer, String> portfolios) {
        final DataFrameRow<Integer, String> row =
              portfolios
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
     * The row is added to the new DataFrame using the same key value (the index of the row).
     * An additional row is added to the extreme right of the risk axis (X), so that
     * a line splitting the lower and upper half of the bullet-shaped portfolios chart
     * can be drawn.
     *
     * @param row the row to add its values to the single row included in the DataFrame
     * @return the DataFrame created from the given row
     * @see #configureChart(Chart)
     */
    private DataFrame<Integer, String> createDataFrameFromRow(final DataFrameRow<Integer, String> row) {
        final DataFrame<Integer, String> df = DataFrame.<Integer, String>ofDoubles(Array.of(row.key(), row.key()+1),
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
     * @param assetsGroup an Array of Assets extracted from {@link #assetsGroups} to generate
     *               random portfolios (each portfolio with different weights for each asset).
     * @return a {@link DataFrame} representing all generated portfolios,
     *         containing the risk and return, from the given assets' group.
     *         Each row in this DataFrame represents a portfolio where the rows are
     *         indexed by the portfolio number (Integer) and the columns are named using the assets names (String).
     *         Each value in the row is a Stream of {@link DataFrameValue} where
     *         the columns are named using the asset name (DataFrameValue colKey)
     *         and each column value is the risk for an asset.
     */
    private DataFrame<Integer, String> computeRiskAndReturnForPortfolios(final ArrayValue<Array<String>> assetsGroup) {
        //The names of the stocks that represent the assets
        final Array<String> tickers = assetsGroup.getValue();

        final YahooFinance yahoo = new YahooFinance();
        final Supplier<DataFrame<LocalDate, String>> dailyReturnsSupplier = () -> yahoo.getDailyReturns(start, end, tickers);
        final Supplier<DataFrame<LocalDate, String>> cumulReturnsSupplier = () -> yahoo.getCumReturns(start, end, tickers);
        final AssetsReturns returns = new AssetsReturns(tickers, dailyReturnsSupplier, cumulReturnsSupplier);

        //Generate random portfolios and compute risk & return for each
        final DataFrame<Integer, String> portfolios = createRandomPortfolios(COUNT, tickers);
        final String label = String.format("%s Assets", tickers.length());

        /*
        A DataFrame to be filled with the overall risk and returns for every generated portfolio of the group.
        Each row is a portfolio (indexed by its Integer number).
        The the values for each row are DataFrameValue objects, each one
        having columns names as the asset name (String).
        */
        final DataFrame<Integer, String> portfoliosRisksReturns =
                DataFrame.ofDoubles(Range.of(0, COUNT), Array.of("Risk", label));

        for (final DataFrameRow<Integer, String> p: portfolios.rows()) {
            computePortfolioRiskAndReturn(p, returns, portfoliosRisksReturns);
        }

        return portfoliosRisksReturns;
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
     *                          The Strings that index the column is used to label such values accordingly.
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
     * @param returns the risk of all assets in the portfolio
     * @param weights the weights for each asset in the portfolio
     * @return the overall portfolio risk.
     */
    private double computePortfolioRisk(final AssetsReturns returns, final DataFrame<Integer, String> weights) {
        /* Since the dot product operation between two arrays/matrices is a single scalar value,
         *  the value from the row 0 and column 0 is being got from the resulting DataFrame. */
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
        //The risks for every asset in every portfolio for the first group of assets.
        final DataFrame<Integer,String> risksOfFirstGroup = portfoliosByGroup.getValue(0);

        //Chart.create().htmlMode();  //Globally enables HTML mode (it doesn't create a chart in fact)
        final Chart<XyPlot<Integer>> chart =
                Chart.create()
                        .withScatterPlot(
                                risksOfFirstGroup, false, "Risk", this::configureChart);

        new Thread(() -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
            }
            chart.writerPng(new File("portfolios-analysis-"+COUNT+"-assets.png"), 800, 600, true);
        }).start();
    }

    private void configureChart(final Chart<XyPlot<Integer>> chart) {
        final int dotsDiameter = 4;
        chart.plot().render(0).withDots(dotsDiameter);
        /*
        Inserts the additional groups of portfolios to be plotted.
        The loop starts at 1 because the first frame is passed to the Chart.create method.
        */
        for (int i = 1; i < portfoliosByGroup.length(); ++i) {
            chart.plot().<String>data().add(portfoliosByGroup.getValue(i), "Risk");
            chart.plot().render(i).withDots(dotsDiameter);
        }

        /** Creates a line between the most efficient portfolio and a point
         *  at the extreme right of the x axis.
         *  This last row in the array of portfolios represents
         *  a DataFrame containing the most efficient portfolio for the last group of assets.
         *  The data for this line is creates using the method {@link #createDataFrameFromRow(ArrayValue)}
         */
        chart.plot().render(portfoliosByGroup.length()-1).withLines(true, false);

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
     *                  For each row, the Integer value is the asset weight and the String is the asset acronym
     */
    private DataFrame<Integer,String> createRandomPortfolios(final int n, final Iterable<String> assets) {
        final DataFrame<Integer,String> weights = DataFrame.ofDoubles(Range.of(0, n), assets);

        //Generates random weights (percentages from 0 to 1) for each asset in the DataFrameRow
        weights.applyDoubles(v -> Math.random());

        weights.rows().forEach(this::normalizePortfolioWeights);
        return weights;
    }

    /**
     * Normalizes the weights for each asset in the portfolio from the data contained at a {@link DataFrameRow}.
     * The Integer value at the {@link DataFrameRow} is the asset's weight and the String is the
     * asset's acronym.
     * Since the sum of the randomly generated weights can be greater than 1 (100%),
     * this function normalizes than so that all weights add up to 1.
     *
     * @param portfolio the {@link DataFrameRow} representing the portfolio to normalize the weights
     */
    private void normalizePortfolioWeights(final DataFrameRow<Integer, String> portfolio) {
        //The sum of all weights for each asset contained in the DataFrameRow representing the portfolio
        final double totalWeights = portfolio.stats().sum();

        //v.getDouble() is the weight for the asset in the portfolio
        portfolio.applyDoubles(v -> v.getDouble() / totalWeights);
    }

}
