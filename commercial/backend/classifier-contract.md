# ClosetAI commercial classifier contract

Use `commercial/shared/wardrobe-taxonomy-v1.json` as the canonical taxonomy.

## Classification output

For every detected wardrobe item return JSON with:

- audience
- department
- group
- category
- product_type
- outfit_slot
- brand
- brand_confidence
- brand_evidence
- primary_colour
- secondary_colours
- material
- material_confidence
- fabric_family
- pattern
- fit
- sleeve
- collar_or_neckline
- length
- waist_rise
- denim_treatment
- closure
- size_label
- occasions[]
- vibes[]
- seasons[]
- works_with_colours[]
- style_tags[]
- weather_tags[]
- confidence
- raw_vendor_text
- raw_ocr_text
- warnings[]

## Hard rules

1. Brand identity is evidence-based.
   - Accept visible logo/wordmark, readable garment label, OCR, receipt/link metadata, or explicit user confirmation.
   - Never infer a brand from visual style alone.
   - If evidence is insufficient, brand = null and brand_confidence = 0.

2. Preserve uncertainty.
   - Image-only fabric classification is an estimate.
   - Do not convert "looks like linen" into confirmed linen.
   - Store material_confidence and a warning when relevant.

3. Separate ecommerce hierarchy from styling hierarchy.
   - department/group/category/product_type describe what the item is.
   - outfit_slot describes how it participates in an outfit.

4. Multi-item images.
   - Detect all separable garments.
   - Return each as an individual item candidate.
   - Also allow a linked Full Outfit record for the complete look.

5. Canonicalization.
   - Normalize aliases through brand_aliases.
   - Normalize colour spelling and fit terminology.
   - Prefer canonical product_type values from the taxonomy.
   - Preserve the model/vendor wording in raw_vendor_text.

6. Search and filters.
   - Multiple occasions, vibes, seasons and colours are allowed.
   - Missing filters are null/[] rather than invented.

## Commercial enrichment

The closet layer adds user-owned state that ecommerce sites do not have:
- availability
- laundry status
- condition
- storage location
- last worn
- wear count
- favourite
- purchase source
- purchase price
- current value
- cost per wear
- authenticity evidence
- AI confidence
- travel ready
- weather suitability
- needs tailoring

These fields must never be overwritten by image classification unless the user explicitly asks.
