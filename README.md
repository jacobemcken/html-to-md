# html-to-md

A Clojure library for converting HTML (e.g. from websites)
into clean Markdown that is both human readable and LLM token-efficient.

[![bb compatible](https://raw.githubusercontent.com/babashka/babashka/master/logo/badge.svg)](https://book.babashka.org#badges)


## Usage

```clojure
(require '[html-to-md.core :as html-to-md])

(html-to-md/convert "<h1>Some heading</h1> <p>Followed by some text</p>")
; "# Some heading\n\nFollowed by some text"
```

## Install

Use it as a cli tool via `bbin`:

    bbin install https://github.com/jacobemcken/html-to-md.git

The use it like:

    html-to-md https://www.emcken.dk


## Development

### Test suite

Running test suite using vanilla Clojure:

    clj -X:test

Running test suite using Babashka:

    bb test


### Build

Build the artifact:

    clj -T:build

Install in local Maven cache:

    clj -T:build install


## Architecture

The library converts HTML to Markdown in two steps:
HTML to Hiccup (via Hickory) and then Hiccup to Markdown.

See [docs/architecture.md](docs/architecture.md) for details.

### Examples

Notice how irrelevant whitespace is trimmed in the following examples.

#### Example 1 - inline content

```html
<span> Some text</span><span>!</span>   <span>More</span><span> text</span>.
```

becomes:
```
Some text! More text.
```

#### Example 2 - block content

```html
<p>Some text!</p><p>More text,
across multiple lines.</p>
```

becomes:
```
Some text!

More text,
across multiple lines.
```

#### Example 3 - mixed content (complicated)

```html
<ul>
  <li>Apple</li>
  <li>Banana
    <ol>
      <li>The <code>yellow</code> one.</li>
      <li><div><blockquote><p>A <em>sunflower</em> would 
          <span style="color:purple;">be jealous</span>.
          </p><p><strong>Even</strong><br>the sun.</p></blockquote></div></li>
    </ol>
  </li>
  <li>Kiwi</li>
</ul>
```

becomes:
```
-   Apple
-   Banana
    1.  The `yellow` one.
    2.  > A _sunflower_ would be jealous.
        > 
        > **Even**  
        > the sun.
-   Kiwi
```
