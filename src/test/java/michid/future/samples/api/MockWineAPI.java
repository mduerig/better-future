package michid.future.samples.api;

import static michid.future.samples.api.WineAPi.Wine.Color.red;
import static michid.future.samples.api.WineAPi.Wine.Color.white;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

import michid.future.samples.api.WineAPi.Review.Stars;

public class MockWineAPI implements WineAPi {
    private static final Wine[] WINES = {
        new Wine(
            "Meursault Premier Cru",
            "Chardonnay", white, 2005,
            "Bourgogne", "France",
            Optional.empty()),
        new Wine(
            "Richebourg Grand Cru",
            "Pinot Nero", red, 1998,
            "Bourgogne",
            "France",
            Optional.of(new Review(
                "Absolutely fantastic, this is not an every day wine.",
                Stars.five
            ))),
        new Wine(
            "Chateau Mouton Rothschild",
            "Merlot, Cabernet Sauvignon", red, 1978,
            "Bordeaux", "France",
            Optional.empty()),
        new Wine(
            "Chateau Lafite Rothschild",
            "Merlot, Cabernet Sauvignon, Petit Verdot", red, 1978,
            "Bordeaux", "France",
            Optional.empty()),
        new Wine(
            "Pessac-Leognan (Premier Grand Cru Classe)",
            "Merlot", red, 1978,
            "Bordeaux", "France",
            Optional.empty()),
        new Wine(
            "Chateau Latour",
            "Cabernet Sauvignon, Merlot", red, 1998,
            "Bordeaux", "France",
            Optional.empty()),
        new Wine
            ("Chateau Margaux",
             "Merlot, Cabernet Sauvignon, Cabernet Franc", red, 1998,
             "Bordeaux", "France",
             Optional.empty()),
        new Wine(
            "Chateau Haut-Brion",
            "Merlot", red, 1978,
            "Bordeaux", "France",
            Optional.empty()),
        new Wine(
            "Chateau Pavie",
            "Merlot", red, 1998,
            "Bordeaux", "France",
            Optional.empty()),
        new Wine(
            "Chateau Ducru-Beaucaillou Saint-Julien (Grand Cru Classe)",
            "Merlot, Cabernet Sauvignon", red, 1993,
            "Bordeaux", "France",
            Optional.empty())};

    private static final Map<String, WineAPi> VENDORS = Map.of(
        "FineWine", new MockWineAPI(Arrays.stream(WINES).toList()),
        "WineFail", withFailures(new MockWineAPI(Arrays.stream(WINES).toList())));

    private final List<Wine> wines;

    public static WineAPi forVendor(String name) {
        WineAPi wineAPI = VENDORS.get(name);
        if (wineAPI == null) {
            throw new NoSuchElementException(name);
        } else {
            return wineAPI;
        }
    }

    private MockWineAPI(Collection<Wine> wines) {
        this.wines = new ArrayList<>(wines);
    }

    @Override
    public Stream<Wine> getWines() {
        return wines.stream();
    }

    @Override
    public Review getReview(Wine wine) {
        String[] reviews = new String[] {"terrible", "acceptable", "good", "very good", "fantastic"};
        int k = wine.name().length() % 5;
        return new Review(
            "The " + wine.vintage() + " " + wine.name() + " is " + reviews[k] + ".",
            Stars.values()[k]);
    }

    private static WineAPi withFailures(WineAPi delegate) {
        return new WineAPi() {
            @Override
            public Stream<Wine> getWines() throws Exception {
                throw new Exception("bang");
            }

            @Override
            public Review getReview(Wine wine) throws Exception {
                if (wine.vintage() >= 2000) {
                    throw new Exception("no review");
                } else {
                    return delegate.getReview(wine);
                }
            }
        };
    }

}
