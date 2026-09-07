# ClosetAI Fashion Taxonomy v2

ClosetAI uses a normalized retail-grade taxonomy based on the publicly visible classification patterns of AJIO/AJIO Luxe and Tata CLiQ Luxury.

## Hierarchy

Audience -> Department -> Group -> Category -> Product Type

Examples:
- Men -> Clothing -> Western Wear -> Shirts -> Casual Shirts
- Men -> Footwear -> Shoes -> Formal Shoes -> Loafers
- Men -> Accessories -> Watches -> Watches -> Watches
- Women -> Clothing -> Ethnic Wear -> Sarees -> Sarees
- Women -> Accessories -> Bags -> Handbags -> Handbags

## Core facets

- Audience / Shop For
- Department
- Group
- Category
- Product Type
- Brand
- Colour
- Size
- Fit
- Occasion
- Mood / Vibe
- Style tags
- Highlights
- Pattern
- Fabric family
- Fabric composition
- Wash care
- Length
- Sleeve
- Collar
- Neckline
- Waist rise
- Denim treatment
- Transparency
- Fastening / closure

## Category-specific facets

Footwear:
- Upper material
- Insole material
- Sole material
- Toe shape
- Heel type
- Fastening

Watches:
- Strap material
- Dial type
- Dial shape
- Movement
- Water resistance

Jewellery:
- Jewellery material

## Brand handling

Brand classification is evidence-based:
1. local OCR / label text
2. visible logo or wordmark
3. brand alias normalization
4. confidence threshold
5. unknown when evidence is insufficient

The brand catalog is seeded from public marketplace brand visibility and is designed to grow over time. It must not be treated as a static copy of either retailer's catalog.

## Dynamic filters

The backend computes facet counts per authenticated user's wardrobe, so the UI can show filters such as:

Brand
BOSS (12)
Polo Ralph Lauren (8)
Lacoste (7)

without loading all wardrobe items into memory first.

## Current backend

- fashion_taxonomy: 61 seeded product classifications
- facet_catalog: 36 canonical facets
- brand_catalog: 47 normalized seed brands
- classifier version: ClosetAI Vision v3
- taxonomy version: retail-v2

Live retailer catalogs evolve, so taxonomy data should be refreshed periodically rather than hard-coded forever.
