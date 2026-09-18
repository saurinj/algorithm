package com.sj.application.algorithm;
import java.util.*;

/**
 * Most-specific multi-dimensional rule match.
 *
 * Matching semantics:
 *  - A rule matches iff every dimension IT specifies equals the
 *    request's value for that dimension. A dimension a rule omits is a
 *    wildcard. A dimension whose rule-value is a Set (e.g. MCC groups)
 *    matches if the request's value is a member of that set.
 *  - Among matching rules, the one with the most specified (non-RANK)
 *    dimensions wins ("most specific").
 *  - Ties are broken by RANK, ascending lexicographic order ('a' before
 *    'b'); a missing RANK loses to any real RANK.
 *
 * Two matchers, same interface:
 *  - LinearScanMatcher: O(rules) per lookup. Simplest correct thing.
 *  - BucketedMatcher:   O(distinct signatures) per lookup. A signature
 *    is the set of dimension names a rule populates. Rules are grouped
 *    by signature into a hash bucket keyed on the concrete values for
 *    that signature's dimensions, so a lookup does one O(1) probe per
 *    signature instead of a per-rule field-by-field comparison. This is
 *    a hand-rolled, single-level generalization of the "Tuple Space
 *    Search" technique used in packet/ACL classifiers (e.g. Open
 *    vSwitch's flow classifier) -- same idea, scaled to this problem's
 *    size rather than a hardware-classifier's constraints.
 *    
 * Performance:
 * Built 100000 rules across 60 signatures.
 * LinearScanMatcher build: 0.01 ms
 * BucketedMatcher build:   987.35 ms
 * Cross-check mismatches: 0 / 500
 * 
 * LinearScanMatcher: hits=2000/2000 avg=18.7143ms p50=18.0417ms p99=28.5679ms max=55.5521ms
 * BucketedMatcher: hits=2000/2000 avg=0.0040ms p50=0.0031ms p99=0.0093ms max=1.0936ms
 */
public final class ClaudeGeneratedClosestMatch {

    // ---------------------------------------------------------------
    // Rule
    // ---------------------------------------------------------------
    public static final class Rule {
        final String mid;
        final Map<String, Object> dims; // populated dimensions only. Values are
                                         // either a scalar (equality) or a
                                         // Set<Object>.
        final String rank;              // nullable tie-break field

        public Rule(String mid, Map<String, Object> dims, String rank) {
            this.mid = mid;
            this.dims = dims;
            this.rank = rank;
        }

        private int specificity() {
            return dims.size();
        }

        boolean matches(Map<String, Object> request) {
            for (Map.Entry<String, Object> e : dims.entrySet()) {
                Object reqVal = request.get(e.getKey());
                Object ruleVal = e.getValue();
                if (ruleVal instanceof Set) {
                    if (reqVal == null || !((Set<?>) ruleVal).contains(reqVal)) return false;
                } else if (!Objects.equals(reqVal, ruleVal)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return mid;
        }
    }

    static Rule better(Rule a, Rule b) {
        if (a.specificity() != b.specificity()) {
            return a.specificity() > b.specificity() ? a : b;
        }
        String ar = a.rank != null ? a.rank : "￿"; // missing RANK sorts last
        String br = b.rank != null ? b.rank : "￿";
        return ar.compareTo(br) <= 0 ? a : b;
    }

    public interface Matcher {
        Rule lookup(Map<String, Object> request);
    }

    // ---------------------------------------------------------------
    // 1) Baseline: linear scan.
    // ---------------------------------------------------------------
    public static final class LinearScanMatcher implements Matcher {
        private final List<Rule> rules;

        public LinearScanMatcher(List<Rule> rules) {
            this.rules = rules;
        }

        @Override
        public Rule lookup(Map<String, Object> request) {
            Rule best = null;
            for (Rule r : rules) {
                if (r.matches(request)) {
                    best = (best == null) ? r : better(best, r);
                }
            }
            return best;
        }
    }

    // ---------------------------------------------------------------
    // 2) Signature-bucketed hash index.
    // ---------------------------------------------------------------
    public static final class BucketedMatcher implements Matcher {

        // signature -> (value-tuple -> rules sharing that exact signature+values)
        private final Map<List<String>, Map<List<Object>, List<Rule>>> buckets = new HashMap<>();
        // specificity size -> signatures of that size, largest size first
        private final TreeMap<Integer, List<List<String>>> levels =
                new TreeMap<>(Comparator.reverseOrder());

        public BucketedMatcher(List<Rule> rules) {
            for (Rule rule : rules) {
                List<String> dimensions = new ArrayList<>(rule.dims.keySet());
                Collections.sort(dimensions);
                Map<List<Object>, List<Rule>> bucket =
                        buckets.computeIfAbsent(dimensions, s -> new HashMap<>());
                insert(bucket, dimensions, rule);
            }
            for (List<String> sig : buckets.keySet()) {
                levels.computeIfAbsent(sig.size(), k -> new ArrayList<>()).add(sig);
            }
            System.out.println("Completed");
        }

        @SuppressWarnings("unchecked")
        private static void insert(Map<List<Object>, List<Rule>> bucket, List<String> dimensions,
                             Rule rule) {
        	List<List<Object>> tuples = new ArrayList<>();
        	tuples.add(new ArrayList<>());
        	
        	if (rule.mid.equals("1234565") && "a".equals(rule.rank)) {
        		System.out.println("possibly More than 1");
        	}
        	
        	for (String dim : dimensions) {
                Object val = rule.dims.get(dim);
                List<List<Object>> expanded = new ArrayList<>();

                if (val instanceof Set) {
                    // fan out: every existing partial tuple gets copied once per set member
                    for (List<Object> partial : tuples) {
                        for (Object v : (Set<?>) val) {
                            List<Object> next = new ArrayList<>(partial);
                            next.add(v);
                            expanded.add(next);
                        }
                    }
                } else {
                    // scalar: every existing partial tuple just gets this one value appended
                    for (List<Object> partial : tuples) {
                        List<Object> next = new ArrayList<>(partial);
                        next.add(val);
                        expanded.add(next);
                    }
                }
                tuples = expanded;
            }
        	
        	// every fully-built tuple is a real key this rule should be filed under
        	if(tuples.size() > 1) {
        		System.out.println("More than 1");
        	}
            for (List<Object> key : tuples) {
                bucket.computeIfAbsent(List.copyOf(key), k -> new ArrayList<>()).add(rule);
            }
        }

        @Override
        public Rule lookup(Map<String, Object> request) {
            Set<String> reqDims = request.keySet();
            for (Map.Entry<Integer, List<List<String>>> level : levels.entrySet()) {
                List<Rule> hits = null;
                for (List<String> sig : level.getValue()) {
                    if (!reqDims.containsAll(sig)) continue;
                    List<Object> key = new ArrayList<>(sig.size());
                    for (String d : sig) {
                    	key.add(request.get(d));
                    }
                    List<Rule> bucketHits = buckets.get(sig).get(key);
                    if (bucketHits != null) {
                        if (hits == null) hits = new ArrayList<>();
                        hits.addAll(bucketHits);
                    }
                }
                if (hits != null) {
                    Rule best = hits.get(0);
                    for (int i = 1; i < hits.size(); i++) {
                    	best = better(best, hits.get(i));
                    }
                    return best;
                }
            }
            return null;
        }

        int signatureCount() {
            return buckets.keySet().size();
        }
    }

    // ---------------------------------------------------------------
    // Demo / self-check against the original worked example, then a
    // synthetic 100K-rule benchmark sized to the described profile.
    // ---------------------------------------------------------------
    public static void main(String[] args) {
        runWorkedExample();
        runBenchmark();
    }

    private static void runWorkedExample() {
    	Map<String, Object> base = new HashMap<>();
        base.put("dim1", "val1");
        base.put("dim2", "val2");
        base.put("dim3", "val3");

        List<Rule> rules = new ArrayList<>();
        rules.add(rule("1234561", base, Map.of("dim4", "val41",
                "dim5", "val51", "dim6", "val62"), "b"));
        rules.add(rule("1234562", base, Map.of("dim4", "val41",
                "dim5", "val51", "dim6", "val61"), "b"));
        rules.add(rule("1234563", base, Map.of("dim4", "val42",
                "dim5", "val52", "dim6", "val62"), "b"));
        rules.add(rule("1234564", base, Map.of("dim4", "val42",
                "dim5", "val52", "dim6", "val61"), "b"));
        rules.add(rule("1234565", base, Map.of("dim4", "val41",
                "dim6", "val61", "dim7", Set.of("val71", "val72", "val73")), "a"));
        rules.add(rule("1234566", base, Map.of("dim4", "dim41",
                "dim6", "val61", "dim7", Set.of("val74", "val75", "val76")), "a"));
        rules.add(rule("1234567", base, Map.of("dim4", "val41",
                "dim6", "val62"), null));
        rules.add(rule("1234568", base, Map.of("dim4", "val41",
                "dim6", "val61"), null));
        rules.add(rule("1234569", base, Map.of("dim4", "val41",
                "dim6", "val62"), null));

        Map<String, Object> req1 = new HashMap<>(base);
        req1.put("dim4", "val41");
        req1.put("dim5", "val51");
        req1.put("dim6", "val61");
        req1.put("dim7", "val71");
        
        Map<String, Object> req2 = new HashMap<>(req1);
        req2.put("dim7", "val77");

        for (Matcher m : List.of(new LinearScanMatcher(rules), new BucketedMatcher(rules))) {
            Rule r1 = m.lookup(req1);
            Rule r2 = m.lookup(req2);
            System.out.println(m.getClass().getSimpleName() + ": req1 -> " + r1
                    + " (expected 1234565), req2 -> " + r2 + " (expected 1234562)");
            if (!"1234565".equals(r1.mid) || !"1234562".equals(r2.mid)) {
                throw new AssertionError("Worked example mismatch");
            }
        }
        System.out.println("Worked example: PASS\n");
    }

    private static Rule rule(String mid, Map<String, Object> base,
                              Map<String, Object> extra, String rank) {
        Map<String, Object> dims = new HashMap<>(base);
        dims.putAll(extra);
        return new Rule(mid, dims, rank);
    }

    private static void runBenchmark() {
        Random rnd = new Random(42);
        int numAllDims = 120;
        String[] allDims = new String[numAllDims];
        for (int i = 0; i < numAllDims; i++) allDims[i] = "D" + i;

        int numTemplates = 60;
        List<List<String>> templates = new ArrayList<>();
        for (int t = 0; t < numTemplates; t++) {
            int k = 10 + rnd.nextInt(6); // 10..15 populated dims
            List<String> shuffled = new ArrayList<>(Arrays.asList(allDims));
            Collections.shuffle(shuffled, rnd);
            templates.add(new ArrayList<>(shuffled.subList(0, k)));
        }

        int numRules = 100_000;
        List<Rule> rules = new ArrayList<>(numRules);
        int domainSize = 12;
        for (int i = 0; i < numRules; i++) {
            List<String> t = templates.get(rnd.nextInt(numTemplates));
            Map<String, Object> dims = new HashMap<>();
            for (String d : t) dims.put(d, rnd.nextInt(domainSize));
            String rank = rnd.nextDouble() < 0.3
                    ? new String[]{"a", "b", "c"}[rnd.nextInt(3)] : null;
            rules.add(new Rule("MID" + i, dims, rank));
        }

        long t0 = System.nanoTime();
        LinearScanMatcher linear = new LinearScanMatcher(rules);
        long t1 = System.nanoTime();
        BucketedMatcher bucketed = new BucketedMatcher(rules);
        long t2 = System.nanoTime();
        System.out.printf("Built %d rules across %d signatures.%n", numRules, bucketed.signatureCount());
        System.out.printf("LinearScanMatcher build: %.2f ms%n", (t1 - t0) / 1e6);
        System.out.printf("BucketedMatcher build:   %.2f ms%n", (t2 - t1) / 1e6);

        // Guaranteed-hit requests: exact copy of an existing rule's dims.
        int numLookups = 2000;
        List<Map<String, Object>> requests = new ArrayList<>(numLookups);
        for (int i = 0; i < numLookups; i++) {
            requests.add(new HashMap<>(rules.get(rnd.nextInt(numRules)).dims));
        }

        // Correctness cross-check first.
        int mismatches = 0;
        for (Map<String, Object> req : requests.subList(0, 500)) {
            Rule r1 = linear.lookup(req);
            Rule r2 = bucketed.lookup(req);
            String id1 = r1 == null ? null : r1.mid;
            String id2 = r2 == null ? null : r2.mid;
            if (!Objects.equals(id1, id2)) mismatches++;
        }
        System.out.println("Cross-check mismatches: " + mismatches + " / 500");

        // Warm up JIT before timing.
        for (int i = 0; i < 2000; i++) linear.lookup(requests.get(i % requests.size()));
        for (int i = 0; i < 2000; i++) bucketed.lookup(requests.get(i % requests.size()));

        timeMatcher("LinearScanMatcher", linear, requests);
        timeMatcher("BucketedMatcher", bucketed, requests);
    }

    private static void timeMatcher(String name, Matcher matcher, List<Map<String, Object>> requests) {
        double[] times = new double[requests.size()];
        int hits = 0;
        for (int i = 0; i < requests.size(); i++) {
            long t0 = System.nanoTime();
            Rule r = matcher.lookup(requests.get(i));
            long t1 = System.nanoTime();
            times[i] = (t1 - t0) / 1e6;
            if (r != null) hits++;
        }
        Arrays.sort(times);
        double avg = Arrays.stream(times).average().orElse(0);
        double p50 = times[times.length / 2];
        double p99 = times[(int) (times.length * 0.99)];
        double max = times[times.length - 1];
        System.out.printf("%s: hits=%d/%d avg=%.4fms p50=%.4fms p99=%.4fms max=%.4fms%n",
                name, hits, requests.size(), avg, p50, p99, max);
    }
}
