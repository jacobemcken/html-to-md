# html-to-md

A Clojure library for converting HTML into Markdown.

[![bb compatible](https://raw.githubusercontent.com/babashka/babashka/master/logo/badge.svg)](https://book.babashka.org#badges)


## Usage

```clojure
(require '[html-to-md.core :as html-to-md])

(html-to-md/convert "<h1>Some heading</h1> <p>Followed by some text</p>")
; "# Some heading\n\n Followed by some text\n\n"
```

## Development

### Architecture

The library consist of two transformation layers:

```mermaid
graph LR
    A[HTML] -- transform --> B
    B[Hiccup] -- transform --> C
    C[Markdown]
```

An existing library [Hickory][1] already handles HTML to Hiccup convertion quite well,
**so this library just uses Hickory and focuses on Hiccup to Markdown convertion**.
But includes Hickory as a dependency for convenience.


### Test suite

Running test suite:

    clj -X:test


[1]: https://github.com/clj-commons/hickory