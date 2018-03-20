import com.zavtech.morpheus.array.Array;
import com.zavtech.morpheus.array.ArrayValue;
import com.zavtech.morpheus.frame.DataFrame;
import com.zavtech.morpheus.frame.DataFrameRow;
import com.zavtech.morpheus.frame.DataFrameValue;
import com.zavtech.morpheus.range.Range;
import com.zavtech.morpheus.viz.chart.Chart;
import com.zavtech.morpheus.viz.chart.xy.XyPlot;

import java.io.File;
import java.time.LocalDate;

/**
 * Applies the Modern Portfolio Theory (MPT) to plot randomly generated portfolios with different weights
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
 * <p><b>Credits:</b> This example was based on the code available at
 * the <a href="http://www.zavtech.com/morpheus/docs/examples/mpt/#multiple-assets">documentation page</a>
 * for the <a href="https://github.com/zavtech">ZavTech Morpheus Library</a>
 * by <a href="https://github.com/Zavster">Xavier Witdouck</a>.</p>
 * @author Manoel Campos da Silva Filho
 */
public class PortfolioGeneration {
    /**
     * Number of random portfolios to generate for each {@link #assetsGroups group of assets.}
     */
    private static final int COUNT = 10000;
    public static final String SUBTITLE = COUNT + " Randomly Generated Portfolio Combinations";
    public static final String X_AXIS_LABEL = "Portfolio Risk";
    public static final String Y_AXIS_LABEL = "Portfolio Return";
    public static final int PORTFOLIO_RISK_COL = 0;
    public static final int PORTFOLIO_RETURN_COL = 1;
    public String title = "Risk/Return Profiles of Portfolios";

    //Define investment horizon
    private final LocalDate end = LocalDate.of(2018, 03, 17);
    private final LocalDate start = end.minusYears(1);


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
            //Array.of("VNQ", "VEA")
    );

    /**
     * An array of {@link DataFrame} containing the overall risk and returns for every randomly generated portfolio,
     * for every group given by the {@link #assetsGroups}.
     * Each DataFrame represents a portfolio for a group of assets.
     * Each row represents the overall risk and return for that portfolio.
     *
     * @see #computeRiskAndReturnForPortfolios(ArrayValue)
     */
    private final Array<DataFrame<Integer,String>> portfoliosRiskReturnByGroup;

    public static void main(String[] args) {
        new PortfolioGeneration();
    }

    private PortfolioGeneration(){
        if(assetsGroups.length() > 1) {
            title += " with Increasing Number of Assets";
        }

        portfoliosRiskReturnByGroup = assetsGroups.map(this::computeRiskAndReturnForPortfolios);
        plot();
    }

    /**
     * Computes the risk and return for {@link #COUNT} random generated portfolios compounded of a given
     * list of assets.
     *
     * @param assetsGroup an Array of Assets extracted from {@link #assetsGroups} to generate
     *               random portfolios (each portfolio with different weights for each asset).
     * @return a {@link DataFrame} containing the risk and return of all generated portfolios
     *         from the given assets' group.
     *         Each row in this DataFrame represents a portfolio where the rows are
     *         indexed by the portfolio number (Integer) and the columns are named using the assets names (String).
     *         Each value in the row is a Stream of {@link DataFrameValue} where
     *         the columns are named using the asset name (DataFrameValue colKey)
     *         and each column value is the risk for an asset.
     */
    private DataFrame<Integer, String> computeRiskAndReturnForPortfolios(final ArrayValue<Array<String>> assetsGroup) {
        //The names of the stocks that represent the assets
        final Array<String> tickers = assetsGroup.getValue();

        final AssetsReturns returns = new AssetsReturns(tickers, start, end);

        //Generate random portfolios and compute risk & return for each
        final DataFrame<Integer, String> portfolios = createRandomPortfolios(COUNT, tickers);
        final String label = String.format("%s Assets", tickers.length());

        /*
        A DataFrame to be filled with the overall risk and returns for every generated portfolio of the group.
        Each row is a portfolio (indexed by its Integer number).
        The the values for each row are DataFrameValue objects, each one
        having columns names as the asset name (String).
        */
        final DataFrame<Integer, String> assetsRisksReturns =
                DataFrame.ofDoubles(Range.of(0, COUNT), Array.of("Risk", label));

        for (final DataFrameRow<Integer, String> p: portfolios.rows()) {
            computePortfolioRiskAndReturn(p, returns, assetsRisksReturns);
        }

        return assetsRisksReturns;
    }

    /**
     * Computes the overall risk and return for a given potfolio.
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

        assetsRisksReturn.data().setDouble(portfolio.key(), PORTFOLIO_RISK_COL, Math.sqrt(portfolioVariance) * 100d);
        assetsRisksReturn.data().setDouble(portfolio.key(), PORTFOLIO_RETURN_COL, portfolioReturn * 100d);
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
        final DataFrame<Integer,String> risksOfFirstGroup = portfoliosRiskReturnByGroup.getValue(0);

        //Chart.create().htmlMode();  //Globally enables HTML mode (it doesn't create a chart in fact)
        Chart<XyPlot<Integer>> chart =
                Chart.create()
                        .withScatterPlot(
                                risksOfFirstGroup, false, "Risk", this::configureChart);
        chart.writerPng(new File("portfolios-analysis-"+COUNT+"-assets.png"), 800, 600, true);
    }

    private void configureChart(final Chart<XyPlot<Integer>> chart) {
        for (int i = 1; i < portfoliosRiskReturnByGroup.length(); ++i) {
            chart.plot().<String>data().add(portfoliosRiskReturnByGroup.getValue(i), "Risk");
            chart.plot().render(i).withDots();
        }

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
