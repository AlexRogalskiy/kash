package com.beust.kash

import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine
import org.slf4j.LoggerFactory
import java.io.FileReader

/**
 * External tab completers are defined in ~/.kash.json and are simple Kash scripts:
 *
 * {
 *     "completers": [
 *           "gitCompleter.kash.kts"
 *     ]
 * }
 * These scripts are looked up on the `scriptPaths` and they are passed the following arguments:
 * - args[0] (String): the full line typed so far
 * - args[1] (Int): the position of the cursor
 * The script needs to return a `List<String>` containing all the candidates, or an empty
 * list if no completion is possible at this point.
 */
@Suppress("UNCHECKED_CAST")
class ExternalCompleter(private val context: KashContext, val engine: Engine): Completer {
    private val log = LoggerFactory.getLogger(ExternalCompleter::class.java)

    override fun complete(reader: LineReader?, line: ParsedLine, candidates: MutableList<Candidate>) {
        val completers = DotKashJsonReader.dotKash?.completers
        val finder = ScriptFinder()
        completers?.forEach {
            val result = finder.findCommand(it, context)
            if (result != null) {
                val cs = engine.eval(FileReader(result.path), listOf(line.line(), line.cursor().toString()))
                    as List<String>
                cs.forEach { candidate ->
                    candidates.add(Candidate(candidate))
                }
            } else {
                log.warn("Couldn't find tab completer $it")
            }
        }
    }
}