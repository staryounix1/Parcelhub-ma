package com.example.data.supabase

import com.example.domain.model.SubscriptionTier

object SupabaseSchemaSql {
    val fullPostgresSchema: String = """
-- ============================================================================
-- ParcelHub MA — Executable PostgreSQL Schema for Supabase (Multi-Tenant + RLS)
-- Timezone: Africa/Casablanca | Currency: MAD | Phone: E.164 (+212)
-- Compliance: CNDP Law 09-08 & AES-256 / Supabase Vault Secret Protection
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. ENUMS
DO $$ BEGIN
    CREATE TYPE parcel_status AS ENUM (
        'CREATED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY',
        'DELIVERED','REFUSED','RETURNED','CANCELLED'
    );
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    CREATE TYPE carrier_code AS ENUM ('OZONE_EXPRESS','CATHEDIS','DIGYLOG','SENDIT','AMEEX');
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    CREATE TYPE store_platform AS ENUM ('YOUCAN','SHOPIFY','WOOCOMMERCE','GOOGLE_SHEETS');
EXCEPTION WHEN duplicate_object THEN null; END $$;

DO $$ BEGIN
    CREATE TYPE cod_status AS ENUM ('PENDING_CARRIER','COLLECTED_BY_CARRIER','REMITTED_TO_MERCHANT','DISPUTED');
EXCEPTION WHEN duplicate_object THEN null; END $$;

-- 2. UPDATED_AT TRIGGER FUNCTION
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 3. USERS (Merchants linked to auth.users)
CREATE TABLE IF NOT EXISTS public.users (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT UNIQUE NOT NULL,
    brand_name TEXT NOT NULL,
    phone_e164 TEXT CHECK (phone_e164 ~ '^\+212[67][0-9]{8}$'),
    preferred_lang TEXT NOT NULL DEFAULT 'ar' CHECK (preferred_lang IN ('ar','fr')),
    cndp_consent_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. STORES
CREATE TABLE IF NOT EXISTS public.stores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    platform store_platform NOT NULL,
    store_name TEXT NOT NULL,
    store_url TEXT,
    webhook_secret_encrypted BYTEA, -- Encrypted via pgcrypto / Supabase Vault
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. DELIVERY COMPANIES (Encrypted Carrier API Credentials)
CREATE TABLE IF NOT EXISTS public.delivery_companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    carrier carrier_code NOT NULL,
    api_key_encrypted BYTEA NOT NULL, -- pgp_sym_encrypt(key, vault_secret)
    api_secret_encrypted BYTEA,
    webhook_hmac_secret_encrypted BYTEA,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_polled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (merchant_id, carrier)
);

-- 6. STATUS MAPPINGS (Carrier Raw Status -> Unified Status)
CREATE TABLE IF NOT EXISTS public.status_mappings (
    id BIGSERIAL PRIMARY KEY,
    carrier carrier_code NOT NULL,
    raw_status TEXT NOT NULL,
    unified_status parcel_status NOT NULL,
    UNIQUE (carrier, raw_status)
);

-- 7. CUSTOMERS (Normalized +212 Phone & Blacklist Risk)
CREATE TABLE IF NOT EXISTS public.customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL,
    phone_e164 TEXT NOT NULL CHECK (phone_e164 ~ '^\+212[67][0-9]{8}$'),
    city TEXT NOT NULL,
    address TEXT,
    total_orders INT NOT NULL DEFAULT 1,
    return_count INT NOT NULL DEFAULT 0,
    is_blacklisted BOOLEAN NOT NULL DEFAULT FALSE,
    whatsapp_opt_out BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (merchant_id, phone_e164)
);

-- 8. ORDERS
CREATE TABLE IF NOT EXISTS public.orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    store_id UUID REFERENCES public.stores(id) ON DELETE SET NULL,
    customer_id UUID NOT NULL REFERENCES public.customers(id) ON DELETE RESTRICT,
    store_order_ref TEXT NOT NULL,
    store_platform store_platform NOT NULL,
    product_summary TEXT NOT NULL,
    cod_amount_mad NUMERIC(10,2) NOT NULL CHECK (cod_amount_mad >= 0),
    unified_status parcel_status NOT NULL DEFAULT 'CREATED',
    whatsapp_confirmed BOOLEAN,
    store_synced BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (merchant_id, store_platform, store_order_ref)
);

-- 9. SHIPMENTS
CREATE TABLE IF NOT EXISTS public.shipments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    order_id UUID NOT NULL UNIQUE REFERENCES public.orders(id) ON DELETE CASCADE,
    carrier carrier_code NOT NULL,
    tracking_number TEXT NOT NULL,
    label_url TEXT,
    carrier_fee_mad NUMERIC(8,2) NOT NULL DEFAULT 35.00,
    cod_settlement_status cod_status NOT NULL DEFAULT 'PENDING_CARRIER',
    delivered_at TIMESTAMPTZ,
    remitted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (carrier, tracking_number)
);

-- 10. TRACKING LOGS (Idempotent Webhook & Polling Ledger)
CREATE TABLE IF NOT EXISTS public.tracking_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    shipment_id UUID NOT NULL REFERENCES public.shipments(id) ON DELETE CASCADE,
    external_event_id TEXT NOT NULL,
    raw_status TEXT NOT NULL,
    unified_status parcel_status NOT NULL,
    note TEXT,
    source TEXT NOT NULL CHECK (source IN ('WEBHOOK','POLLING','MANUAL')),
    event_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (shipment_id, external_event_id)
);

-- 11. WHATSAPP TEMPLATES & NOTIFICATION LOGS
CREATE TABLE IF NOT EXISTS public.whatsapp_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    scenario TEXT NOT NULL,
    body_darija TEXT NOT NULL,
    body_french TEXT NOT NULL,
    is_meta_approved BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.notification_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    order_id UUID NOT NULL REFERENCES public.orders(id) ON DELETE CASCADE,
    customer_phone TEXT NOT NULL,
    scenario TEXT NOT NULL,
    dedup_key TEXT NOT NULL UNIQUE,
    provider_status TEXT NOT NULL,
    cost_mad NUMERIC(6,3) NOT NULL DEFAULT 0.160,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 12. SUBSCRIPTIONS & USAGE COUNTERS
CREATE TABLE IF NOT EXISTS public.subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL UNIQUE REFERENCES public.users(id) ON DELETE CASCADE,
    plan_code TEXT NOT NULL DEFAULT 'FREE' CHECK (plan_code IN ('FREE','STARTER','PRO','BUSINESS')),
    monthly_parcel_limit INT NOT NULL DEFAULT 50,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    current_period_end TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.usage_counters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    period_ym TEXT NOT NULL, -- e.g. '2026-11'
    parcels_created INT NOT NULL DEFAULT 0,
    whatsapp_sent INT NOT NULL DEFAULT 0,
    UNIQUE (merchant_id, period_ym)
);

-- 13. INDEXES
CREATE INDEX IF NOT EXISTS idx_orders_merchant_status ON public.orders (merchant_id, unified_status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_shipments_tracking ON public.shipments (tracking_number);
CREATE INDEX IF NOT EXISTS idx_customers_phone_blacklist ON public.customers (merchant_id, phone_e164, is_blacklisted);
CREATE INDEX IF NOT EXISTS idx_tracking_logs_shipment ON public.tracking_logs (shipment_id, event_at DESC);
CREATE INDEX IF NOT EXISTS idx_notification_logs_order ON public.notification_logs (order_id, sent_at DESC);

-- 14. UPDATED_AT TRIGGERS
DROP TRIGGER IF EXISTS trg_users_updated_at ON public.users;
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON public.users FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_stores_updated_at ON public.stores;
CREATE TRIGGER trg_stores_updated_at BEFORE UPDATE ON public.stores FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_delivery_companies_updated_at ON public.delivery_companies;
CREATE TRIGGER trg_delivery_companies_updated_at BEFORE UPDATE ON public.delivery_companies FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_customers_updated_at ON public.customers;
CREATE TRIGGER trg_customers_updated_at BEFORE UPDATE ON public.customers FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_orders_updated_at ON public.orders;
CREATE TRIGGER trg_orders_updated_at BEFORE UPDATE ON public.orders FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_shipments_updated_at ON public.shipments;
CREATE TRIGGER trg_shipments_updated_at BEFORE UPDATE ON public.shipments FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_whatsapp_templates_updated_at ON public.whatsapp_templates;
CREATE TRIGGER trg_whatsapp_templates_updated_at BEFORE UPDATE ON public.whatsapp_templates FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_subscriptions_updated_at ON public.subscriptions;
CREATE TRIGGER trg_subscriptions_updated_at BEFORE UPDATE ON public.subscriptions FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 15. ROW LEVEL SECURITY (Multi-tenant Isolation: USING + WITH CHECK)
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stores ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.delivery_companies ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.shipments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tracking_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.whatsapp_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notification_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.usage_counters ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS merchant_own_user ON public.users;
CREATE POLICY merchant_own_user ON public.users FOR ALL USING (auth.uid() = id) WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS merchant_own_stores ON public.stores;
CREATE POLICY merchant_own_stores ON public.stores FOR ALL USING (auth.uid() = merchant_id) WITH CHECK (auth.uid() = merchant_id);

DROP POLICY IF EXISTS merchant_own_carriers ON public.delivery_companies;
CREATE POLICY merchant_own_carriers ON public.delivery_companies FOR ALL USING (auth.uid() = merchant_id) WITH CHECK (auth.uid() = merchant_id);

DROP POLICY IF EXISTS merchant_own_customers ON public.customers;
CREATE POLICY merchant_own_customers ON public.customers FOR ALL USING (auth.uid() = merchant_id) WITH CHECK (auth.uid() = merchant_id);

DROP POLICY IF EXISTS merchant_own_orders ON public.orders;
CREATE POLICY merchant_own_orders ON public.orders FOR ALL USING (auth.uid() = merchant_id) WITH CHECK (auth.uid() = merchant_id);

DROP POLICY IF EXISTS merchant_own_shipments ON public.shipments;
CREATE POLICY merchant_own_shipments ON public.shipments FOR ALL USING (auth.uid() = merchant_id) WITH CHECK (auth.uid() = merchant_id);

DROP POLICY IF EXISTS merchant_own_tracking ON public.tracking_logs;
CREATE POLICY merchant_own_tracking ON public.tracking_logs FOR ALL USING (auth.uid() = merchant_id) WITH CHECK (auth.uid() = merchant_id);

DROP POLICY IF EXISTS merchant_own_templates ON public.whatsapp_templates;
CREATE POLICY merchant_own_templates ON public.whatsapp_templates FOR ALL USING (auth.uid() = merchant_id) WITH CHECK (auth.uid() = merchant_id);

DROP POLICY IF EXISTS merchant_own_notif ON public.notification_logs;
CREATE POLICY merchant_own_notif ON public.notification_logs FOR ALL USING (auth.uid() = merchant_id) WITH CHECK (auth.uid() = merchant_id);

DROP POLICY IF EXISTS merchant_own_sub ON public.subscriptions;
CREATE POLICY merchant_own_sub ON public.subscriptions FOR ALL USING (auth.uid() = merchant_id) WITH CHECK (auth.uid() = merchant_id);

DROP POLICY IF EXISTS merchant_own_usage ON public.usage_counters;
CREATE POLICY merchant_own_usage ON public.usage_counters FOR ALL USING (auth.uid() = merchant_id) WITH CHECK (auth.uid() = merchant_id);
""".trimIndent()

    val subscriptionTiers: List<SubscriptionTier> = listOf(
        SubscriptionTier(
            id = "FREE",
            nameAr = "مجاني (Free)",
            nameFr = "Gratuit (Free)",
            priceMadMonthly = 0,
            monthlyParcelLimit = 50,
            storesLimit = 1,
            carriersLimit = 1,
            featuresAr = listOf("50 طرد/شهر", "متجر واحد + شركة توصيل واحدة", "تتبع أساسي + إشعارات يدوية"),
            featuresFr = listOf("50 colis/mois", "1 boutique + 1 transporteur", "Suivi de base")
        ),
        SubscriptionTier(
            id = "STARTER",
            nameAr = "انطلاقة (Starter)",
            nameFr = "Starter",
            priceMadMonthly = 149,
            monthlyParcelLimit = 400,
            storesLimit = 2,
            carriersLimit = 3,
            featuresAr = listOf("400 طرد/شهر", "تأكيد واتساب أوتوماتيكي", "مطابقة COD + قائمة سوداء"),
            featuresFr = listOf("400 colis/mois", "Confirmation WhatsApp auto", "Rapprochement COD + Blacklist")
        ),
        SubscriptionTier(
            id = "PRO",
            nameAr = "احترافي (Pro)",
            nameFr = "Pro",
            priceMadMonthly = 349,
            monthlyParcelLimit = 1500,
            storesLimit = 5,
            carriersLimit = 5,
            featuresAr = listOf("1,500 طرد/شهر", "جميع الشركات الخمس + Polling", "تحليلات Retour حسب المدينة"),
            featuresFr = listOf("1 500 colis/mois", "5 transporteurs + Polling", "Analytics Retour par ville"),
            isRecommended = true
        ),
        SubscriptionTier(
            id = "BUSINESS",
            nameAr = "شركات (Business)",
            nameFr = "Business",
            priceMadMonthly = 799,
            monthlyParcelLimit = 5000,
            storesLimit = 20,
            carriersLimit = 5,
            featuresAr = listOf("5,000 طرد/شهر", "صلاحيات فريق العمل + API خاص", "دعم أولوية + تقارير مالية"),
            featuresFr = listOf("5 000 colis/mois", "Multi-utilisateurs + API", "Support prioritaire & exports")
        )
    )
}
