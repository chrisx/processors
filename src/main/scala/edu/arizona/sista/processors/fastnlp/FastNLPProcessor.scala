package edu.arizona.sista.processors.fastnlp

import edu.arizona.sista.processors.corenlp.CoreNLPProcessor
import edu.arizona.sista.processors.{ Sentence, Document }
import org.maltparserx.MaltParserService
import FastNLPProcessor._
import scala.collection.mutable.{ ListBuffer, ArrayBuffer }
import edu.arizona.sista.processors.utils.Files
import scala.collection.mutable
import edu.arizona.sista.processors.struct.DirectedGraph
import org.maltparserx

/**
 * Fast NLP tools
 * Uses most of CoreNLP but replaces its parser with maltparser
 * This means that constituent trees and coreference, which depends on that, are not available
 * Also, malt produces Stanford "basic" dependencies, rather than "collapsed" ones
 * User: mihais
 * Date: 1/4/14
 */

import akka.actor._
import akka.routing._
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout

class MPActor extends Actor with TimeX {
  println("Loaded MPActor")
  private def mkArgs(workDir: String, modelName: String): String = {
    val args = new ArrayBuffer[String]()
    args += "-m"
    args += "parse"
    args += "-w"
    args += workDir
    args += "-c"
    args += modelName
    args += "-v"
    args += "error"
    args.mkString(" ")
  }
  lazy val args = mkArgs(Files.mkTmpDir("maltwdir-" + System.nanoTime, deleteOnExit = true), DEFAULT_MODEL_NAME)
  val maltService = {
    val service = timeX(new MaltParserService, "new MaltParserService")
    timeX(service.initializeParserModel(args), "s.initializeParserModel(args)")
    service
  }

  def receive = {
    case tokens: Array[String] =>
      //println("MPActor received tokens: " + tokens.size)
      val parsed: Array[String] = timeX(maltService.parseTokens(tokens), "maltService.parseTokens(tokens)")
      //println("MPActor parsed: " + parsed.size + " replying")
      sender ! parsed
    case m => println("Unknown message: " + m)
  }

  override def postStop = {
    println("MPActor shutdown")
  }
}

trait TimeX {
  protected def timeX[A](f: => A, desc: String = "", onlySlow: Boolean = true, slowLimit: Long = 500) = {
    val start = System.currentTimeMillis
    val ret = f
    val end = System.currentTimeMillis - start
    if (!onlySlow || (onlySlow && end > slowLimit)) println(Thread.currentThread.getId + " " + desc + " Took " + end + "ms")
    ret
  }
}

class FastNLPProcessor(internStrings: Boolean = true, val system: ActorSystem) extends CoreNLPProcessor(internStrings) with TimeX {
  /**
   * One maltparser instance for each thread
   * MUST have one separate malt instance per thread!
   * malt uses a working directory which is written at runtime
   * using ThreadLocal variables guarantees that each thread gets its own working directory
   */

  // not used by Kadaxis
  override def parse(doc: Document) {
    val annotation = basicSanityCheck(doc)
    if (annotation.isEmpty) return
    if (doc.sentences.head.tags == None)
      throw new RuntimeException("ERROR: you have to run the POS tagger before NER!")
    if (doc.sentences.head.lemmas == None)
      throw new RuntimeException("ERROR: you have to run the lemmatizer before NER!")

    // parse each individual sentence
    for (sentence <- doc.sentences) {
      val dg = parseSentence(sentence)
      sentence.dependencies = Some(dg)
    }
  }

  // Kadaxis
  // added parseForPassive and parseSentenceForPassive originally as a workaround for a bug in
  // the original FastNLPProcessor that threw an exception in DirectedGraph when parsing: Have it your own way . '' (from a submitted manuscript)
  // but added this version to reduce parsing effort (and only look for NSUBJPASS which is what we need) and added isPassive to Sentence
  def parseForPassive(doc: Document) {
    val annotation = basicSanityCheck(doc)
    if (annotation.isEmpty) return
    if (doc.sentences.head.tags == None)
      throw new RuntimeException("ERROR: you have to run the POS tagger before NER!")
    if (doc.sentences.head.lemmas == None)
      throw new RuntimeException("ERROR: you have to run the lemmatizer before NER!")

    // parse each individual sentence
    for (sentence <- doc.sentences.par) {
      sentence.isPassive = timeX(parseSentenceForPassive(sentence), "\t parseSentenceForPassive(sentence)")
    }
  }

  implicit val timeout = Timeout(30 seconds)
  private lazy val parserServiceActor = system.actorSelection("*user/MPServiceActors*")
  def parseTokens(tokens: Array[String]) = {
    val future = parserServiceActor ? tokens
    try Await.result(future, 30 seconds).asInstanceOf[Array[String]]
    catch {
      case e: Throwable =>
        println("\n\n\nHave mpServiceActors been started?\n\n\n")
        throw e
    }

  }

  // Kadaxis
  def parseSentenceForPassive(sentence: Sentence): Boolean = {
    val tokens = new Array[String](sentence.words.length)
    for (i <- 0 until tokens.length) {
      tokens(i) = s"${i + 1}\t${sentence.words(i)}\t${sentence.lemmas.get(i)}\t${sentence.tags.get(i)}\t${sentence.tags.get(i)}\t_"
    }

    // the actual parsing
    //val service = timeX(getService(), "getService")
    //val output = timeX(service.parseTokens(tokens), "service.parseTokens(tokens)")
    val output = timeX(parseTokens(tokens), "parseTokens(tokens)")
    for (o <- output) {
      val tokens = o.split("\\s+")
      if (tokens(7) == "NSUBJPASS") return true
    }
    false
  }

  /** Parses one sentence and stores the dependency graph in the sentence object */
  private def parseSentence(sentence: Sentence): DirectedGraph[String] = {
    // tokens stores the tokens in the input format expected by malt (CoNLL-X)
    val tokens = new Array[String](sentence.words.length)
    for (i <- 0 until tokens.length) {
      tokens(i) = s"${i + 1}\t${sentence.words(i)}\t${sentence.lemmas.get(i)}\t${sentence.tags.get(i)}\t${sentence.tags.get(i)}\t_"
    }

    // the actual parsing
    //val output = getService().parseTokens(tokens)
    val output = parseTokens(tokens)

    // convert malt's output into our dependency graph
    val edgeBuffer = new ListBuffer[(Int, Int, String)]
    val roots = new mutable.HashSet[Int]
    for (o <- output) {
      //println(o)
      val tokens = o.split("\\s+")
      if (tokens.length < 8)
        throw new RuntimeException("ERROR: Invalid malt output line: " + o)
      // malt indexes tokens from 1; we index from 0
      val modifier = tokens(0).toInt - 1
      val head = tokens(6).toInt - 1
      val label = tokens(7).toLowerCase()

      if (label == "root" && head == -1) {
        roots += modifier
      } else {
        edgeBuffer += new Tuple3(head, modifier, in(label))
      }
    }
    new DirectedGraph[String](edgeBuffer.toList, roots.toSet)
  }

}

object FastNLPProcessor {
  val DEFAULT_MODEL_NAME = "nivreeager-en-crammer"
}
