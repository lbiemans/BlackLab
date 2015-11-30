/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.search;

import nl.inl.blacklab.search.TextPatternPositionFilter.Operation;
import nl.inl.blacklab.search.sequences.TextPatternExpansion;
import nl.inl.blacklab.search.sequences.TextPatternRepetition;
import nl.inl.blacklab.search.sequences.TextPatternSequence;

/**
 * Describes some pattern of words in a content field. The point of this interface is to provide an
 * abstract layer to describe the pattern we're interested in, which can then be translated into,
 * for example, a SpanQuery object or a String, depending on our needs.
 */
public abstract class TextPattern implements Cloneable {
	/**
	 * Default constructor; does nothing.
	 */
	public TextPattern() {
		//
	}

	/**
	 * Translate this TextPattern into some other representation.
	 *
	 * For example, TextPatternTranslatorSpanQuery translates it into Lucene SpanQuerys.
	 *
	 * @param translator
	 *            the translator to use
	 * @param context
	 *            query execution context to use
	 *
	 * @param <T>
	 *            type of object to translate to
	 * @return result of the translation
	 */
	public abstract <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context);

	/**
	 * Translate this TextPattern into some other representation.
	 *
	 * For example, TextPatternTranslatorSpanQuery translates it into Lucene SpanQuerys.
	 *
	 * Uses the searcher's initial query execution context.
	 *
	 * @param translator
	 *            the translator to use
	 * @param searcher
	 * 			  our searcher, to get the inital query execution context from
	 *
	 *
	 * @param <T>
	 *            type of object to translate to
	 * @return result of the translation
	 */
	public <T> T translate(TextPatternTranslator<T> translator, Searcher searcher) {
		return translate(translator, searcher.getDefaultExecutionContext());
	}

	/**
	 * Translate this TextPattern into some other representation.
	 *
	 * For example, TextPatternTranslatorSpanQuery translates it into Lucene SpanQuerys.
	 *
	 * Used a simple default query execution context not tied to a Searcher. Useful for testing.
	 *
	 * @param translator
	 *            the translator to use
	 *
	 * @param <T>
	 *            type of object to translate to
	 * @return result of the translation
	 */
	public <T> T translate(TextPatternTranslator<T> translator) {
		return translate(translator, QueryExecutionContext.getSimple("contents"));
	}

	/**
	 * Does this TextPattern match the empty sequence?
	 *
	 * For example, the query [word="cow"]* matches the empty sequence. We need to know this so we
	 * can generate the appropriate queries. A query of the form "AB*" would be translated into
	 * "A|AB+", so each component of the query actually generates non-empty matches.
	 *
	 * When translating, the translator will usually generate a query that doesn't match the empty
	 * sequence, because this is not a practical query to execute. It is up to the translator to
	 * make sure the empty-sequence-matching property of the pattern is correctly dealt with at a
	 * higher level (usually by executing two alternative queries, one with and one without the part
	 * that would match the empty sequence). It uses this method to do so.
	 *
	 * We default to no because most queries don't match the empty sequence.
	 *
	 * @return true if this pattern matches the empty sequence, false otherwise
	 */
	public boolean matchesEmptySequence() {
		return false;
	}

	@Override
	public String toString() {
		return toString("fieldName");
	}

	public String toString(Searcher searcher) {
		return toString(searcher, "fieldName");
	}

	public String toString(String fieldName) {
		return translate(new TextPatternTranslatorString(), QueryExecutionContext.getSimple(fieldName));
	}

	public String toString(Searcher searcher, String fieldName) {
		return translate(new TextPatternTranslatorString(), searcher.getDefaultExecutionContext());
	}

	/**
	 * Rewrite the TextPattern before translation.
	 *
	 * This changes the structure of certain queries so they can be executed
	 * more efficiently.
	 *
	 * @return either the original TextPattern (if no rewriting was necessary),
	 * or the rewritten TextPattern
	 */
	public TextPattern rewrite() {
		return this;
	}

	/**
	 * Return an inverted version of this TextPattern.
	 *
	 * @return the inverted TextPattern
	 */
	public TextPattern inverted() {
		return new TextPatternNot(this);
	}

	/**
	 * Is it okay to invert this TextPattern for optimization?
	 *
	 * Heuristic used to determine when to optimize
	 * a query by inverting one or more of its subqueries.
	 *
	 * @return true if it is, false if not
	 */
	protected boolean okayToInvertForOptimization() {
		return false;
	}

	/**
	 * Is this (sub)pattern only a negative clause, excluding things?
	 *
	 * Used for optimization decisions, i.e. in TextPatternOr.rewrite().
	 *
	 * @return true if it's negative-only, false if not
	 */
	public boolean isNegativeOnly() {
		return false;
	}

	/**
	 * Try to combine with the previous part into a repetition pattern.
	 *
	 * This optimized queries like "blah" "blah" into "blah"{2}, which
	 * executes more efficiently.
	 *
	 * @param previousPart the part occurring before this one in a sequence
	 * @return a combined repetition text pattern, or null if it can't be combined
	 */
	public TextPattern combineWithPrecedingPart(TextPattern previousPart) {
		if (previousPart instanceof TextPatternRepetition) {
			// Repetition clause.
			TextPatternRepetition rep = (TextPatternRepetition) previousPart;
			TextPattern prevCl = rep.getClause();
			if (equals(prevCl)) {
				// Same clause; add one to rep's min and max
				return new TextPatternRepetition(this, 1 + rep.getMin(), 1 + rep.getMax());
			}
		} else if (equals(previousPart)) {
			// Same clause; create repetition with min and max equals 2.
			return new TextPatternRepetition(this, 2, 2);
		} else if (previousPart instanceof TextPatternExpansion) {
			TextPatternExpansion tp = (TextPatternExpansion)previousPart;
			if (tp.isExpandToLeft() && tp.getMinExpand() != tp.getMaxExpand()) {
				// Expand to left with a range of tokens. Combine with this part to likely
				// reduce the number of hits we'll have to expand.
				TextPattern seq = new TextPatternSequence(tp.getClause(), this);
				seq = seq.rewrite();
				return new TextPatternExpansion(seq, true, tp.getMinExpand(), tp.getMaxExpand());
			}
		} else if (hasConstantLength()) {
			if (previousPart instanceof TextPatternPositionFilter) {
				// We are "gobbled up" by the previous part and adjust its right matching edge inward.
				// This should make filtering more efficient, since we will likely have fewer hits to filter.
				try {
					TextPatternPositionFilter result = (TextPatternPositionFilter)previousPart.clone();
					result.clauses.set(0, new TextPatternSequence(result.clauses.get(0), this));
					result.adjustRight(-getMinLength());
					return result;
				} catch (CloneNotSupportedException e) {
					throw new RuntimeException(e);
				}
			} else if (isNegativeOnly() && previousPart.hasConstantLength()) {
				// Negative, constant-length child after constant-length part.
				// Rewrite to NOTCONTAINING clause, incorporating previous part.
				int prevLen = previousPart.getMinLength();
				int myLen = getMinLength();
				TextPattern container = new TextPatternExpansion(previousPart, false, myLen, myLen);
				TextPatternPositionFilter result = new TextPatternPositionFilter(container, inverted(), Operation.CONTAINING, true);
				result.adjustLeft(prevLen);
				return result;
			}
		}

		return null;
	}

	@Override
	public abstract boolean equals(Object obj);

	public abstract boolean hasConstantLength();

	public abstract int getMinLength();

	public abstract int getMaxLength();

	@Override
	public abstract int hashCode();

}