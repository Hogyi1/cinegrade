package hu.elte.ik.thesis.cinegrade.domain.enums;

public enum Operation {
    GREATER_OR_EQUAL("≥") {
        @Override
        public boolean test(int photoRating, int targetRating) {
            return photoRating >= targetRating;
        }
    },
    LESS_OR_EQUAL("≤") {
        @Override
        public boolean test(int photoRating, int targetRating) {
            return photoRating <= targetRating;
        }
    },
    EQUAL("=") {
        @Override
        public boolean test(int photoRating, int targetRating) {
            return photoRating == targetRating;
        }
    };

    private final String symbol;

    Operation(String symbol) {
        this.symbol = symbol;
    }

    public abstract boolean test(int photoRating, int targetRating);

    @Override
    public String toString() {
        return symbol;
    }
}
