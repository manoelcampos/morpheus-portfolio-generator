import com.zavtech.morpheus.array.Array;
import com.zavtech.morpheus.array.ArrayValue;
import com.zavtech.morpheus.frame.DataFrame;
import com.zavtech.morpheus.frame.DataFrameRow;
import com.zavtech.morpheus.range.Range;
import com.zavtech.morpheus.viz.chart.Chart;
import com.zavtech.morpheus.viz.chart.xy.XyPlot;
import com.zavtech.morpheus.yahoo.YahooFinance;

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

    //Define investment horizon
    private final LocalDate end = LocalDate.now();
    private final LocalDate start = end.minusYears(1);

    /**
     * Gets stock market data from Yahoo Finance.
     */
    private final YahooFinance yahoo = new YahooFinance();

    /**
     * An Array of Arrays of Assets representing groups of assetsGroups
     * to be used to create portfolios using only the assetsGroups in the group.
     * Each group represents the assetsGroups to be used
     * to generate {@link #COUNT} portfolios, each one with different random weights for the containing
     * assetsGroups.
     *
     * One can add internal Arrays with different number of assetsGroups to show the
     * generated portfolios from these assetsGroups behave in terms of risk and return,
     * as the number of assetsGroups increases.
     */
    private final Array<Array<String>> assetsGroups = Array.of(Array.of("VWO", "VNQ", "VEA"), Array.of("VNQ", "VEA"));

    /**
     * An array of {@link DataFrame} containing the risks for every asset of all randomly generated portfolios,
     * for every group given by the {@link #assetsGroups}.
     *
     * @see #computeRiskAndReturnForPortfolios(ArrayValue)
     */
    private final Array<DataFrame<Integer,String>> portfoliosRisksByGroup;

    public static void main(String[] args) {
        new PortfolioGeneration();
    }

    private PortfolioGeneration(){
        portfoliosRisksByGroup = assetsGroups.map(this::computeRiskAndReturnForPortfolios);

        //The risks for every asset in every portfolio for the first group of assets.
        final DataFrame<Integer,String> risksOfFirstGroup = portfoliosRisksByGroup.getValue(0);

        //Chart.create().htmlMode();  //Globally enables HTML mode (it doesn't create a chart in fact)
        Chart.create()
                .<Integer,String>withScatterPlot(
                        risksOfFirstGroup, false, "Risk", this::configureChart);
    }

    /**
     * Computes the risk and return for random generated portfolios compounded of a given
     * list of assets.
     *
     * @param assetsGroup an Array of Assets extracted from {@link #assetsGroups} to generate the
     *               random portfolios (each portfolio with different weights for each asset).
     * @return a {@link DataFrame} containing the risk for each asset in all generated portfolios
     *         from the given assets' group.
     *         Each row in this DataFrame represents a portfolio.
     *         Each value in the row represents the risk for a given asset in this portfolio.
     */
    private DataFrame<Integer, String> computeRiskAndReturnForPortfolios(final ArrayValue<Array<String>> assetsGroup) {
        //The names of the stocks that represent the assets
        final Array<String> tickers = assetsGroup.getValue();

        //Grab daily returns and cumulative returns from Yahoo Finance
        final DataFrame<LocalDate, String> dayReturns = yahoo.getDailyReturns(start, end, tickers);
        final DataFrame<LocalDate, String> cumReturns = yahoo.getCumReturns(start, end, tickers);

        //Compute asset covariance matrix from daily returns and annualize
        final DataFrame<String, String> covarianceMatrix = dayReturns.cols().stats().covariance().applyDoubles(x -> x.getDouble() * 252);
        final DataFrame<LocalDate, String> assetsReturns = cumReturns.rows().last().map(DataFrameRow::toDataFrame).get();

        //Generate random portfolios and compute risk & return for each
        final String label = String.format("%s Assets", tickers.length());
        final DataFrame<Integer, String> portfolios = createRandomPortfolios(COUNT, tickers);
        final DataFrame<Integer, String> assetsRisks = DataFrame.ofDoubles(Range.of(0, COUNT), Array.of("Risk", label));

        portfolios.rows().forEach(p -> plotPortfolio(p, covarianceMatrix, assetsReturns, assetsRisks));

        return assetsRisks;
    }

    /**
     * Plots the point of a specific portfolio at the chart.
     *
     * @param portfolio a row representing a portfolio, where the Integer value is the asset weight and the String is the asset acronym
     * @param covarianceMatrix the matrix of covariance between every pair of assets
     * @param assetReturns the returns of all assets
     * @param assetsRisks the risk of all assets
     */
    private void plotPortfolio(
            final DataFrameRow<Integer, String> portfolio,
            final DataFrame<String, String> covarianceMatrix,
            final DataFrame<LocalDate, String> assetReturns,
            final DataFrame<Integer, String> assetsRisks)
    {
        DataFrame<Integer, String> weights = portfolio.toDataFrame();
        double portfolioReturn = weights.dot(assetReturns.transpose()).data().getDouble(0, 0);
        double portfolioVariance = weights.dot(covarianceMatrix).dot(weights.transpose()).data().getDouble(0, 0);
        assetsRisks.data().setDouble(portfolio.key(), 1, portfolioReturn * 100d);
        assetsRisks.data().setDouble(portfolio.key(), 0, Math.sqrt(portfolioVariance) * 100d);
    }

    private void configureChart(final Chart<XyPlot<Integer>> chart) {
        for (int i = 1; i < portfoliosRisksByGroup.length(); ++i) {
            chart.plot().<String>data().add(portfoliosRisksByGroup.getValue(i), "Risk");
            chart.plot().render(i).withDots();
        }

        chart.plot().axes().domain().label().withText("Portfolio Risk");
        chart.plot().axes().domain().format().withPattern("0.00'%';-0.00'%'");
        chart.plot().axes().range(0).label().withText("Portfolio Return");
        chart.plot().axes().range(0).format().withPattern("0.00'%';-0.00'%'");
        chart.title().withText("Risk/Return Profiles of Portfolios With Increasing Number of Assets");
        chart.subtitle().withText(COUNT + " Randomly Generated Portfolio Combinations");
        chart.legend().on().bottom();
        chart.show();
    }

    /**
     * Generates N long only random portfolios with weights that add up to 1,
     * all the them containing a given list of assets.
     *
     * @param n         the number of portfolios (rows) in the DataFrame
     * @param assets    the assets to include for the generated portfolios (a list of the asset's acronyms)
     * @return          the {@link DataFrameRow} of N random generated portfolios (1 per row).
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
