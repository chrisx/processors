package edu.arizona.sista.processors

import collection.mutable
import collection.mutable.ListBuffer
import java.lang.StringBuilder
import edu.arizona.sista.processors.struct.{ Tree, DirectedGraph }

/**
 * Stores all annotations for one document
 * User: mihais
 * Date: 3/1/13
 */
class Document(
    val sentences: Array[Sentence],
    var coreferenceChains: Option[CorefChains]) extends Serializable {

  def this(sa: Array[Sentence]) {
    this(sa, None)
  }

  /** Clears any internal state potentially constructed by the annotators */
  def clear() {}
}

/** Stores the annotations for a single sentence */
class Sentence(
    /** Actual tokens in this sentence */
    val words: Array[String],
    /** Start character offsets for the words; start at 0 */
    val startOffsets: Array[Int],
    /** End character offsets for the words; start at 0 */
    val endOffsets: Array[Int],
    /** POS tags for words */
    var tags: Option[Array[String]],
    /** Lemmas */
    var lemmas: Option[Array[String]],
    /** NE labels */
    var entities: Option[Array[String]],
    /** Normalized values of named/numeric entities, such as dates */
    var norms: Option[Array[String]],
    /** Shallow parsing labels */
    var chunks: Option[Array[String]],
    /** Constituent tree of this sentence; includes head words */
    var syntacticTree: Option[Tree[String]],
    /** DAG of syntactic dependencies; word offsets start at 0 */
    var dependencies: Option[DirectedGraph[String]],

    // added by Kadaxis
    var sentiments: Option[Array[String]],
    var isPassive: Boolean) extends Serializable {

  def this(
    words: Array[String],
    startOffsets: Array[Int],
    endOffsets: Array[Int]) =
    this(words, startOffsets, endOffsets,
      None, None, None, None, None, None, None, None, false)

  def size: Int = words.length

  def getSentenceText(): String = {
    var text: String = ""
    for (i <- 0 until words.size) {
      text += words(i)
      if (i < words.size - 1) text += " "
    }
    text
  }

}

/** Stores a single coreference mention */
class CorefMention(
    /** Index of the sentence containing this mentions; starts at 0 */
    val sentenceIndex: Int,
    /** Token index for the mention head word; starts at 0 */
    val headIndex: Int,
    /** Start token offset in the sentence; starts at 0 */
    val startOffset: Int,
    /** Offset of token immediately after this mention; starts at 0 */
    val endOffset: Int,
    /** Id of the coreference chain containing this mention; -1 if singleton mention */
    val chainId: Int) extends Serializable {

  def length = (endOffset - startOffset)

  override def equals(other: Any): Boolean = {
    other match {
      case that: CorefMention => (sentenceIndex == that.sentenceIndex &&
        headIndex == that.headIndex &&
        startOffset == that.startOffset &&
        endOffset == that.endOffset)
      case _ => false
    }
  }

  override def hashCode = {
    41 * (41 * (41 * (41 + sentenceIndex))) +
      41 * (41 * (41 + headIndex)) +
      41 * (41 + startOffset) +
      endOffset
  }

  override def toString: String = {
    val os = new StringBuilder
    os.append("(")
    os.append(sentenceIndex)
    os.append(", ")
    os.append(headIndex)
    os.append(", ")
    os.append(startOffset)
    os.append(", ")
    os.append(endOffset)
    os.append(")")
    os.toString
  }
}

/** Stores all the coreference chains extracted in one document */
class CorefChains(rawMentions: Iterable[CorefMention]) extends Serializable {

  /**
   * Indexes all mentions in a document by sentence index (starting at 0) and head index (starting at 0)
   * This means we store only one mention per head (unlike CoreNLP which may have multiple)
   * In case a specific processor maintains multiple mentions with same head, we keep the longest
   */
  val mentions: Map[(Int, Int), CorefMention] = CorefChains.mkMentions(rawMentions)

  /**
   * Indexes all coreference chains in a document using a unique id per chain
   * These do not include singleton clusters
   */
  val chains: Map[Int, Iterable[CorefMention]] = CorefChains.mkChains(mentions)

  /** Fetches the mention with this sentence and head indices */
  def getMention(sentenceIndex: Int, headIndex: Int): Option[CorefMention] =
    mentions.get((sentenceIndex, headIndex))

  /** Fetches the coreference chain for the mention with this sentence and head indices; None for singletons */
  def getChain(sentenceIndex: Int, headIndex: Int): Option[Iterable[CorefMention]] = {
    getMention(sentenceIndex, headIndex).foreach(m => return getChain(m))
    None
  }

  /** Fetches the coreference chain for this mention; None for singletons */
  def getChain(mention: CorefMention): Option[Iterable[CorefMention]] = {
    if (mention.chainId == -1) return None
    chains.get(mention.chainId)
  }

  /** All recognized chains, without singletons */
  def getChains: Iterable[Iterable[CorefMention]] = chains.values

  /** All mentions in this document */
  def getMentions: Iterable[CorefMention] = mentions.values

  def isEmpty = (mentions.size == 0 && chains.size == 0)
}

object CorefChains {
  private def lessThanForMentions(x: CorefMention, y: CorefMention): Boolean = {
    if (x.sentenceIndex < y.sentenceIndex) return true
    if (x.sentenceIndex > y.sentenceIndex) return false

    if (x.headIndex < y.headIndex) return true
    if (x.headIndex > y.headIndex) return false

    val diffSize = (x.endOffset - x.startOffset) - (y.endOffset - y.startOffset)
    if (diffSize > 0) return true
    if (diffSize < 0) return false

    true
  }

  private def mkMentions(rawMentions: Iterable[CorefMention]): Map[(Int, Int), CorefMention] = {
    // if multiple mentions with same head exist, keep only the longest
    val sortedMentions = rawMentions.toList.sortWith(lessThanForMentions)
    val mentionMap = new mutable.HashMap[(Int, Int), CorefMention]
    var prevMention: CorefMention = null
    for (m <- sortedMentions) {
      // println(m.sentenceIndex + " " + m.headIndex + " " + m.startOffset + " " + m.endOffset)

      // eliminate duplicate mentions (same sentence, same head)
      // if found, we keep the previous, which is guaranteed to be longer due to sorting criterion
      if (prevMention != null &&
        prevMention.sentenceIndex == m.sentenceIndex &&
        prevMention.headIndex == m.headIndex) {
        assert(prevMention.length >= m.length)
      } else {
        mentionMap += (m.sentenceIndex, m.headIndex) -> m
      }

      prevMention = m
    }
    mentionMap.toMap
  }

  private def mkChains(mentions: Map[(Int, Int), CorefMention]): Map[Int, Iterable[CorefMention]] = {
    val chainBuffer = new mutable.HashMap[Int, ListBuffer[CorefMention]]
    for (m <- mentions.values) {
      var cb = chainBuffer.get(m.chainId)
      if (cb == None) {
        val cbv = new ListBuffer[CorefMention]
        chainBuffer += m.chainId -> cbv
        cb = chainBuffer.get(m.chainId)
        assert(cb != None)
      }
      cb.get += m
    }
    val chainMap = new mutable.HashMap[Int, Iterable[CorefMention]]
    for (cid <- chainBuffer.keySet) {
      chainMap += cid -> chainBuffer.get(cid).get.toList
    }
    chainMap.toMap
  }
}