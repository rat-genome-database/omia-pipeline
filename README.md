# omia-pipeline

Loads gene-disease annotations from [OMIA (Online Mendelian Inheritance in Animals)](https://omia.org) into RGD.

## Species

Dog and pig (configurable in `AppConfigure.xml`).

## Source Data

| File | URL | Description |
|------|-----|-------------|
| `causal_mutations.txt` | `https://www.omia.org/curate/causal_mutations/?format=gene_table` | Gene-phene causal mutation records |
| `omia.xml.gz` | `https://www.omia.org/static/omia.xml.gz` | Full OMIA XML dump (phenes, articles, pubmed links) |
| `RGD_OMIA_matching.xlsx` | local (curated) | Manual mapping of OMIA phene IDs / OMIA IDs to RDO term accessions |
| `old_new_ncbi_gene_id_pairs.txt` | local (curated) | Remapping of obsolete NCBI Gene IDs to current ones |

Downloaded files are retained with a date stamp (e.g. `causal_mutations.txt_20250113`). If the newly downloaded file has the same MD5 as the most recent local copy, it is discarded and processing may be skipped.

## Processing Flow

1. **Download** `causal_mutations.txt` and `omia.xml.gz` — skip if unchanged (MD5 comparison).
2. **Optionally stop** early if no new files and `stopProcessingIfNoNewFiles=true`.
3. **Parse `causal_mutations.txt`** — extract gene symbol, NCBI Gene ID, OMIA ID, taxon ID, and phene name for the configured species. Apply old→new NCBI Gene ID remapping where applicable.
4. **Parse `omia.xml`** via StAX streaming — build maps:
   - `omia_id → phene_id` (from `Phene` table)
   - `phene_id → [article_id, ...]` (from `Phene_Article` table)
   - `article_id → pubmed_id` (from `Article` table)
5. **Read `RGD_OMIA_matching.xlsx`** — map phene IDs and OMIA IDs to RDO term accessions.
6. **For each causal mutation record:**
   - Look up the RDO term accession (by phene ID, then fall back to OMIA ID).
   - If no mapping found → log to `mismatched_phenes.log` and skip.
   - Resolve the RGD gene by NCBI Gene ID (EntrezGene xdb); fall back to gene symbol if configured.
   - If the RDO term is obsolete, follow `replaced_by` synonym chain to find the replacement.
   - Build an `Annotation` with evidence code `IAGP`, data source `OMIA`, and PubMed IDs (up to 150) in the xref field. The phene name is stored in the `NOTES` field.
   - Upsert the annotation (insert if new, update `last_modified_date` if existing).
7. **Delete stale annotations** — any OMIA annotation not touched during this run (with a 5-minute grace period) is deleted.
8. **Log annotation counts** before and after for each species.

## Annotation Properties

| Property | Value |
|----------|-------|
| Data source | `OMIA` |
| Evidence code | `IAGP` |
| Reference RGD ID | `12801476` |
| Max PubMed IDs | `150` |
| Notes | OMIA phene name |

## Logs

| File | Contents |
|------|----------|
| `logs/summary.log` | Run summary — emailed after each run |
| `logs/inserted.log` | Newly inserted annotations |
| `logs/updated.log` | Updated annotations |
| `logs/deleted.log` | Deleted stale annotations |
| `logs/mismatched_phenes.log` | Phene terms with no matching RDO term in `RGD_OMIA_matching.xlsx` |
| `logs/excess_pubmeds.log` | Genes with more than 150 PubMed IDs |

Mismatched phenes and excess pubmeds summary logs are also emailed if non-empty.

## Running

```bash
./run.sh
```
