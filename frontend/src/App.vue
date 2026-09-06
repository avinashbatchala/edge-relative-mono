<script setup lang="ts">
const areas = [
  {
    title: 'Market context',
    description:
      'No market feed is connected. Prices and charts are unavailable.',
  },
  {
    title: 'Strategy workspace',
    description:
      'No strategy state is loaded. There are no signals or recommendations.',
  },
  {
    title: 'Execution & risk',
    description:
      'No broker is connected. Order entry and trading controls are unavailable.',
  },
] as const
</script>

<template>
  <div class="workstation">
    <header class="masthead">
      <a
        class="brand"
        href="#main"
        aria-label="Edge Relative, skip to workstation"
      >
        <span class="brand-mark" aria-hidden="true">er.</span>
        <span
          >Edge Relative<span class="brand-caption"
            >Operator workstation</span
          ></span
        >
      </a>
      <span class="connection"
        ><span class="status-dot" aria-hidden="true"></span>Not connected</span
      >
    </header>

    <main id="main" tabindex="-1">
      <section class="intro" aria-labelledby="workstation-title">
        <div>
          <p class="eyebrow">Workstation / Foundation</p>
          <h1 id="workstation-title">
            A clear view.<br /><span>Nothing live. Yet.</span>
          </h1>
          <p class="intro-copy">
            A space for market context, strategy observation, and execution
            oversight. For now, this is a static interface placeholder.
          </p>
        </div>
        <aside class="availability" aria-labelledby="availability-title">
          <p class="eyebrow">Connection required</p>
          <h2 id="availability-title">Trading unavailable</h2>
          <p>
            This scaffold has no backend connection, market data, or broker
            integration.
          </p>
          <p class="availability-note">
            No live or simulated trading is running.
          </p>
        </aside>
      </section>

      <section class="workspace" aria-labelledby="workspace-title">
        <div class="section-heading">
          <h2 id="workspace-title">Workspace outline</h2>
          <span>Static placeholder</span>
        </div>
        <div class="areas">
          <article v-for="area in areas" :key="area.title" class="area">
            <span class="area-marker" aria-hidden="true"></span>
            <h3>{{ area.title }}</h3>
            <p>{{ area.description }}</p>
          </article>
        </div>
      </section>
      <p class="authority-note">
        Presentation only. Trading decisions, risk enforcement, and
        authoritative order state belong to backend services, not this browser.
      </p>
    </main>

    <footer>
      <span>Edge Relative</span
      ><span>Frontend foundation / No operational connection</span>
    </footer>
  </div>
</template>
