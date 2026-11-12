(() => {
    const formatIndianAmount = (rawAmount, currencyCode = '') => {
        const match = String(rawAmount).trim().match(/^([+-]?)(\d+)(?:\.(\d*))?$/);
        if (!match) return String(rawAmount);

        const negative = match[1] === '-';
        let integer = match[2].replace(/^0+(?=\d)/, '');
        const rawFraction = match[3] ?? '';
        const isRupee = currencyCode.toUpperCase() === 'INR';
        let fraction;

        if (isRupee) {
            const roundedFraction = rawFraction.padEnd(3, '0');
            let minorUnits = Number(roundedFraction.slice(0, 2));
            if (roundedFraction[2] >= '5') minorUnits += 1;
            if (minorUnits === 100) {
                minorUnits = 0;
                const digits = integer.split('');
                for (let index = digits.length - 1; index >= 0; index--) {
                    if (digits[index] !== '9') {
                        digits[index] = String(Number(digits[index]) + 1);
                        break;
                    }
                    digits[index] = '0';
                    if (index === 0) digits.unshift('1');
                }
                integer = digits.join('');
            }
            fraction = String(minorUnits).padStart(2, '0');
        } else {
            fraction = rawFraction.replace(/0+$/, '');
            while (fraction.length < 2) fraction += '0';
        }

        let groupedInteger;
        if (isRupee) {
            const trailingGroup = integer.slice(-3);
            let precedingGroups = integer.slice(0, -3);
            const groups = [];
            while (precedingGroups.length > 2) {
                groups.unshift(precedingGroups.slice(-2));
                precedingGroups = precedingGroups.slice(0, -2);
            }
            if (precedingGroups) groups.unshift(precedingGroups);
            groups.push(trailingGroup);
            groupedInteger = groups.join(',');
        } else {
            groupedInteger = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
        }

        const sign = negative && (integer !== '0' || fraction !== '00') ? '-' : '';
        return `${sign}${isRupee ? '₹' : ''}${groupedInteger}.${fraction}`;
    };

    document.querySelectorAll('[data-money]').forEach((amount) => {
        amount.textContent = formatIndianAmount(amount.textContent, amount.dataset.currency);
    });

    const toggle = document.querySelector('[data-menu-toggle]');
    const sidebar = document.querySelector('#main-sidebar');
    const backdrop = document.querySelector('[data-sidebar-backdrop]');

    if (toggle && sidebar && backdrop) {
        const setOpen = (open) => {
            sidebar.classList.toggle('is-open', open);
            backdrop.classList.toggle('is-visible', open);
            toggle.setAttribute('aria-expanded', String(open));
            document.body.classList.toggle('menu-open', open);
        };

        toggle.addEventListener('click', () => setOpen(!sidebar.classList.contains('is-open')));
        backdrop.addEventListener('click', () => setOpen(false));
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') setOpen(false);
        });
    }

    document.querySelectorAll('[data-dialog-open]').forEach((trigger) => {
        trigger.addEventListener('click', () => {
            const dialog = document.getElementById(trigger.dataset.dialogOpen);
            if (dialog instanceof HTMLDialogElement) dialog.showModal();
        });
    });

    document.querySelectorAll('[data-dialog-close]').forEach((trigger) => {
        trigger.addEventListener('click', () => trigger.closest('dialog')?.close());
    });

    const operationForm = document.querySelector('#money-operation-form');
    const operationSelect = operationForm?.querySelector('[data-operation-select]');
    const sourceAccount = operationForm?.querySelector('[data-source-account]');
    const destinationAccount = operationForm?.querySelector('[data-destination-account]');
    const destinationField = operationForm?.querySelector('[data-destination-field]');
    const currencyInput = operationForm?.querySelector('#currencyCode');
    const amountInput = operationForm?.querySelector('#amount');
    const confirmation = document.querySelector('#operation-confirm-dialog');

    if (operationForm && operationSelect && sourceAccount && destinationAccount && destinationField && currencyInput) {
        const operationLabels = { DEPOSIT: 'Deposit', WITHDRAWAL: 'Withdrawal', TRANSFER: 'Transfer' };
        const updateOperation = () => {
            const action = operationSelect.value;
            operationForm.action = `/transactions/${action.toLowerCase()}`;
            destinationField.hidden = action !== 'TRANSFER';
            destinationAccount.required = action === 'TRANSFER';
            operationForm.querySelector('[data-source-label]').textContent = action === 'DEPOSIT' ? 'Into account' : 'From account';
            operationForm.querySelector('[data-submit-label]').textContent = action === 'DEPOSIT' ? 'Review deposit' : action === 'WITHDRAWAL' ? 'Review withdrawal' : 'Review transfer';
            const reviewOperation = document.querySelector('[data-review-operation]');
            if (reviewOperation) reviewOperation.textContent = operationLabels[action] ?? 'Transaction';
            const destinationRow = document.querySelector('[data-review-destination-row]');
            if (destinationRow) destinationRow.hidden = action !== 'TRANSFER';
            syncAccounts();
        };

        const syncAccounts = () => {
            const sourceOption = sourceAccount.selectedOptions[0];
            const currency = sourceOption?.dataset.currency ?? '';
            if (currency) currencyInput.value = currency;
            const digits = currency ? new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions().maximumFractionDigits : 2;
            if (amountInput) amountInput.step = String(1 / (10 ** digits));
            const sourceCurrency = sourceOption?.dataset.currency;
            Array.from(destinationAccount.options).forEach((option) => {
                if (option.value) option.disabled = option.value === sourceAccount.value || (sourceCurrency && option.dataset.currency !== sourceCurrency);
            });
            if (destinationAccount.selectedOptions[0]?.disabled) destinationAccount.value = '';
            const prefix = document.querySelector('[data-currency-prefix]');
            if (prefix) prefix.textContent = currency === 'INR' ? '₹' : currency || '¤';
        };

        operationSelect.addEventListener('change', updateOperation);
        sourceAccount.addEventListener('change', syncAccounts);
        updateOperation();

        operationForm.addEventListener('submit', (event) => {
            if (operationForm.dataset.submitted === 'true') {
                event.preventDefault();
                return;
            }
            if (operationForm.dataset.confirmed !== 'true' && confirmation instanceof HTMLDialogElement) {
                event.preventDefault();
                const sourceName = sourceAccount.selectedOptions[0]?.textContent?.trim() || '—';
                const destinationName = destinationAccount.selectedOptions[0]?.textContent?.trim() || '—';
                const amount = amountInput?.value || '—';
                const currency = currencyInput.value || '';
                document.querySelector('[data-review-amount]').textContent = formatIndianAmount(amount, currency);
                document.querySelector('[data-review-source]').textContent = sourceName;
                document.querySelector('[data-review-destination]').textContent = destinationName;
                confirmation.showModal();
                return;
            }
            operationForm.dataset.submitted = 'true';
            operationForm.setAttribute('aria-busy', 'true');
            const submitButton = operationForm.querySelector('[data-submit-label]');
            if (submitButton) {
                submitButton.disabled = true;
                submitButton.textContent = 'Posting securely…';
            }
        });

        document.querySelector('[data-operation-cancel]')?.addEventListener('click', () => confirmation?.close());
        document.querySelector('[data-operation-confirm]')?.addEventListener('click', () => {
            confirmation?.close();
            operationForm.dataset.confirmed = 'true';
            operationForm.requestSubmit();
        });
    }

    document.querySelectorAll('form[data-loading-state]').forEach((form) => {
        form.addEventListener('submit', () => {
            form.setAttribute('aria-busy', 'true');
            const submitButton = form.querySelector('button[type="submit"], input[type="submit"]');
            if (!submitButton || submitButton.disabled) return;
            submitButton.disabled = true;
            if (submitButton instanceof HTMLInputElement) {
                submitButton.value = submitButton.dataset.loadingLabel ?? 'Please wait…';
            } else {
                submitButton.textContent = submitButton.dataset.loadingLabel ?? 'Please wait…';
            }
        });
    });

    document.querySelectorAll('[data-report-chart]').forEach((chart) => {
        const points = Array.from(chart.querySelectorAll('[data-trend-point]')).map((point) => ({
            month: point.dataset.month,
            deposits: Number(point.dataset.deposits) || 0,
            withdrawals: Number(point.dataset.withdrawals) || 0,
            transfers: Number(point.dataset.transfers) || 0
        }));
        if (points.length === 0) {
            chart.hidden = true;
            return;
        }

        const namespace = 'http://www.w3.org/2000/svg';
        const width = 760;
        const height = 240;
        const left = 49;
        const right = 12;
        const top = 15;
        const bottom = 36;
        const plotWidth = width - left - right;
        const plotHeight = height - top - bottom;
        const colors = { deposits: '#34a989', withdrawals: '#7d91b9', transfers: '#c3a366' };
        const labels = { deposits: 'Deposits', withdrawals: 'Withdrawals', transfers: 'Transfers' };
        const max = Math.max(1, ...points.flatMap((point) => [point.deposits, point.withdrawals, point.transfers]));
        const niceMax = max * 1.12;
        const make = (name, attributes = {}) => {
            const node = document.createElementNS(namespace, name);
            Object.entries(attributes).forEach(([key, value]) => node.setAttribute(key, String(value)));
            return node;
        };
        const grid = make('g', { 'aria-hidden': 'true' });
        for (let line = 0; line <= 4; line++) {
            const fraction = line / 4;
            const y = top + plotHeight - fraction * plotHeight;
            grid.append(make('line', { x1: left, x2: width - right, y1: y, y2: y, class: 'chart-gridline' }));
            const value = make('text', { x: left - 8, y: y + 3, 'text-anchor': 'end', class: 'chart-axis-label' });
            const locale = chart.dataset.currency === 'INR' ? 'en-IN' : 'en-US';
            value.textContent = new Intl.NumberFormat(locale, { maximumFractionDigits: 0 }).format(niceMax * fraction);
            grid.append(value);
        }
        chart.append(grid);

        const groupWidth = plotWidth / points.length;
        const barWidth = Math.max(1, Math.min(18, groupWidth * 0.19));
        const series = ['deposits', 'withdrawals', 'transfers'];
        points.forEach((point, index) => {
            const center = left + groupWidth * (index + 0.5);
            series.forEach((key, seriesIndex) => {
                const amount = point[key];
                if (amount <= 0) return;
                const barHeight = Math.max(1, (amount / niceMax) * plotHeight);
                const x = center + (seriesIndex - 1) * (barWidth + 2) - barWidth / 2;
                const y = top + plotHeight - barHeight;
                const bar = make('rect', { x, y, width: barWidth, height: barHeight, fill: colors[key] });
                const title = make('title');
                title.textContent = `${labels[key]}: ${formatIndianAmount(String(amount), chart.dataset.currency)} (${point.month})`;
                bar.append(title);
                chart.append(bar);
            });

            const stride = Math.max(1, Math.ceil(points.length / 12));
            if (index % stride === 0 || index === points.length - 1) {
                const label = make('text', { x: center, y: height - 11, 'text-anchor': 'middle', class: 'chart-month-label' });
                const date = new Date(`${point.month}-01T00:00:00Z`);
                label.textContent = new Intl.DateTimeFormat(undefined, { month: 'short', year: '2-digit', timeZone: 'UTC' }).format(date);
                chart.append(label);
            }
        });
    });
})();
