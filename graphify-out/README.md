# Knowledge graph

A [graphify](https://github.com/Graphify-Labs/graphify) knowledge graph of this
repository, so an agent (or a person) can navigate by structure instead of
grepping every file.

| File | What it is |
|---|---|
| `GRAPH_REPORT.md` | Human-readable report: hub nodes, communities, surprising connections |
| `graph.html` | Interactive graph, open in a browser |
| `graph.json.gz` | The graph itself, gzipped |
| `manifest.json` | Per-file hashes, so `--update` only re-extracts what changed |
| `cost.json` | Token spend per run |

## Using the graph

The tools read `graph.json`, which is committed gzipped because the raw file is
60 MB — large enough that GitHub warns about it and, once pushed, it stays in
the history forever. Unpack it once after cloning:

```bash
gunzip -k graphify-out/graph.json.gz
```

Then:

```bash
graphify query "how does the community CORS filter decide what to allow?"
graphify path "CorsFilter" "AesGcmUtil"
graphify explain "CommunityEncryptionKeyStore"
graphify god-nodes --top 20
```

`graph.json` is gitignored, so unpacking it will not show up as a change.

## Rebuilding

```bash
pip install graphifyy
graphify update .        # incremental: only re-extracts changed files
```

Re-compress before committing:

```bash
gzip -9 -c graphify-out/graph.json > graphify-out/graph.json.gz
```

## What this graph does and does not cover

Built from **AST extraction only** — the structural pass over source files. It
needs no API key and no LLM.

Not included:

- **Docs and images** (56 + 53 files). Those need the semantic extraction pass,
  which requires either a Gemini key or a fan-out of LLM subagents.
- **`.sql` files** (6). Their parser is an optional dependency:
  `pip install "graphifyy[sql]"`, then re-run.
- **Community names.** The 652 communities carry placeholder labels
  (`Community 0`, `Community 1`, …) because naming them is an LLM step. Run
  `graphify label .` with a backend configured to name them.

Roughly 60 files — mostly `.json` config — parsed to zero nodes and are absent
from the graph. That is expected for data files with no code structure.
