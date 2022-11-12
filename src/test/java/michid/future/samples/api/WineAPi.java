package michid.future.samples.api;

import java.util.Optional;
import java.util.stream.Stream;

public interface WineAPi {
    record Wine(
        String name,
        String grapes,
        Color color,
        int vintage,
        String region,
        String country,
        Optional<Review> review
    )
    {
        enum Color {RED, WHITE, PINK}

        public Wine withReview(Review review) {
            return new Wine(name, grapes, color, vintage, region, country, Optional.of(review));
        }

        @Override
        public String toString() {
            return
                name + "\n" +
                grapes + ", " + color + ", " + vintage + "\n" +
                region + ", " + country + "\n" +
                review.map(Object::toString).orElse("");
        }
    }

    record Review(
        String review,
        Stars rating
    )
    {
        public enum Stars {ONE, TWO, THREE, FOUR, FIVE}

        @Override
        public String toString() {
            return review + " " + (rating.ordinal() + 1) + " Star";
        }
    }

    Stream<Wine> getWines() throws Exception;
    Review getReview(Wine wine) throws Exception;
}
