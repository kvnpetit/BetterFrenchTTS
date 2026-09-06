/**
 * SSML (Speech Synthesis Markup Language) node tree and renderer.
 *
 * [SsmlNode] is a sealed hierarchy representing SSML elements (text, break, prosody, emphasis,
 * say-as, sentence, paragraph). [SsmlRenderer] converts the node tree into a complete SSML XML
 * string wrapped in a `<speak>` root element.
 *
 * This is an internal layer — library consumers interact with the
 * [DSL][io.github.kvnpetit.betterfrenchtts.dsl.SpeechBuilder] which builds nodes automatically.
 *
 * @see SsmlNode
 * @see SsmlRenderer
 */
package io.github.kvnpetit.betterfrenchtts.ssml
