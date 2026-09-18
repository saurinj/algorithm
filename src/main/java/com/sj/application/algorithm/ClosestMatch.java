package com.sj.application.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


public class ClosestMatch {

	private static class Rule {
		private String property;
		private Map<String, Object> dims;
		private String rank;
		
		public Rule (String property, Map<String, Object> dims, String rank) {
			this.property = property;
			this.dims = dims;
			this.rank = rank;
		}
		
		public int dimSize() {
			return dims.size();
		}
		
		@Override
		public String toString() {
			return "Rule#" + property;
		}
	}
	
	private static class Matcher {
		
		/**
		 * buckets:
		 * [dim1, dim2, dim3, dim4, dim6]
		 *     [val1, val2, val3, val41, val62] -> [Rule#1234567]
		 *     [val1, val2, val3, val42, val62] -> [Rule#1234569]
		 *     [val1, val2, val3, val41, val61] -> [Rule#1234568]
		 * [dim1, dim2, dim3, dim4, dim6, dim7]
		 *     [val1, val2, val3, dim41, val61, val76] -> [Rule#1234566]
		 *     [val1, val2, val3, dim41, val61, val75] -> [Rule#1234566]
		 *     [val1, val2, val3, val41, val61, val71] -> [Rule#1234565]
		 *     [val1, val2, val3, val41, val61, val72] -> [Rule#1234565]
		 *     [val1, val2, val3, val41, val61, val73] -> [Rule#1234565]
		 *     [val1, val2, val3, dim41, val61, val74] -> [Rule#1234566]
		 * [dim1, dim2, dim3, dim4, dim5, dim6]
		 *     [val1, val2, val3, val42, val52, val61] -> [Rule#1234564]
		 *     [val1, val2, val3, val41, val51, val61] -> [Rule#1234562]
		 *     [val1, val2, val3, val42, val52, val62] -> [Rule#1234563]
		 *     [val1, val2, val3, val41, val51, val62] -> [Rule#1234561]
		 *     
		 * levels:
		 * 6
		 *     [dim1, dim2, dim3, dim4, dim6, dim7]
		 *     [dim1, dim2, dim3, dim4, dim5, dim6]
		 * 5
		 *     [dim1, dim2, dim3, dim4, dim6]
		 * 
		 */
		private TreeMap<Integer, List<List<String>>> levels = new TreeMap<>(Comparator.reverseOrder());
		private Map<List<String>, Map<List<Object>, List<Rule>>> buckets = new HashMap<>();
		
		public Matcher(List<Rule> rules) {
			for (Rule rule: rules) {
				List<String> dimensionKeys = new ArrayList<>(rule.dims.keySet());
				Collections.sort(dimensionKeys);
				
				Map<List<Object>, List<Rule>> bucket = buckets.computeIfAbsent(dimensionKeys, s -> new HashMap<>());
				insert(bucket, dimensionKeys, rule);
			}
			for (List<String> dims : buckets.keySet()) {
				levels.computeIfAbsent(dims.size(), s -> new ArrayList<>()).add(dims);
			}
			System.out.println("Setup completed:");
			printBuckets(buckets);
			printLevels(levels);
		}

		private static void printBuckets(Map<List<String>, Map<List<Object>, List<Rule>>> buckets) {
			System.out.println("buckets:");
		    for (Map.Entry<List<String>, Map<List<Object>, List<Rule>>> outer : buckets.entrySet()) {
		        System.out.println(outer.getKey());
		        for (Map.Entry<List<Object>, List<Rule>> inner : outer.getValue().entrySet()) {
		            System.out.println("    " + inner.getKey() + " -> " + inner.getValue());
		        }
		    }
		}

		private static void printLevels(Map<Integer, List<List<String>>> levels) {
			System.out.println("levels:");
			for (Map.Entry<Integer, List<List<String>>> level : levels.entrySet()) {
		        System.out.println(level.getKey());
		        for (List<String> inner : level.getValue()) {
		            System.out.println("    " + inner);
		        }
		    }
		}

		private void insert(Map<List<Object>, List<Rule>> bucket, List<String> dimensionKeys, Rule rule) {
			List<List<Object>> tuples = new ArrayList<>();
			tuples.add(new ArrayList<>());
			
			for (String dim : dimensionKeys) {
				Object value = rule.dims.get(dim);
				List<List<Object>> extended = new ArrayList<>();
				if (value instanceof Set) {
					for (List<Object> partial : tuples) {
						Set<?> values = (Set<?>)value;
						for (Object val : values) {
							List<Object> newList = new ArrayList<>(partial);
							newList.add(val);
							extended.add(newList);
						}
					}
				} else {
					for (List<Object> partial : tuples) {
						List<Object> newList = new ArrayList<>(partial);
						newList.add(value);
						extended.add(newList);
					}
				}
				tuples = extended;
			}
			
			for (List<Object> tuple : tuples) {
				bucket.computeIfAbsent(tuple, s -> new ArrayList<>()).add(rule);
			}
		}
		
		public Rule lookup(Map<String, Object> input) {
			for (Map.Entry<Integer, List<List<String>>> level : levels.entrySet()) {
				List<Rule> hits = null;
				for (List<String> dimKeys : level.getValue()) {
					List<Object> values = new ArrayList<>();
					for (String dimKey : dimKeys) {
						Object value = input.get(dimKey);
						values.add(value);
					}
					List<Rule> matchingRules = buckets.get(dimKeys).get(values);
					if (matchingRules != null) {
						if (hits == null) {
							hits = new ArrayList<>();
						}
						hits.addAll(matchingRules);
					}
					
				}
				if (hits != null) {
					Rule bestRule =  hits.get(0);
					for (Rule rule : hits) {
						bestRule = better(bestRule, rule);
					}
					if (bestRule != null) {
						return bestRule;
					}
				}
				
			}
			return null;
			
		}
		
		private static Rule better(Rule a, Rule b) {
			if (a.equals(b)) {
				return a;
			}
			if (a.dimSize() > b.dimSize()) {
				return a;
			} else if (b.dimSize() > a.dimSize()) {
				return b;
			}
			String ar = a.rank;
			String br = b.rank;
			if (ar != null) {
				return ar.compareTo(br) <= 0 ? a : b;
			} else if (br != null) {
				return br.compareTo(ar) <= 0 ? b : a;
			}
			throw new RuntimeException("better is failing for a=" + a.property + " and b=" + b.property);
		}
	}
	
	public static void main(String[] args) {
        runWorkedExample();
        //runBenchmark();
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
        rules.add(rule("1234569", base, Map.of("dim4", "val42",
                "dim6", "val62"), null));
        
        Matcher matcher = new Matcher(rules);

        Map<String, Object> req1 = new HashMap<>(base);
        req1.put("dim4", "val41");
        req1.put("dim5", "val51");
        req1.put("dim6", "val61");
        req1.put("dim7", "val71");

        Rule r1 = matcher.lookup(req1);
        System.out.println("1st property lookup:" + r1.property);
        
        Map<String, Object> req2 = new HashMap<>(req1);
        req2.put("dim7", "val77");
        
        Rule r2 = matcher.lookup(req2);
        System.out.println("2nd property lookup:" + r2.property);
        
        if (!"1234565".equals(r1.property) || !"1234562".equals(r2.property)) {
            throw new AssertionError("Worked example mismatch");
        }
    }

    private static Rule rule(String property, Map<String, Object> base,
        Map<String, Object> extra, String rank) {
		Map<String, Object> dims = new HashMap<>(base);
		dims.putAll(extra);
		return new Rule(property, dims, rank);
}
}
