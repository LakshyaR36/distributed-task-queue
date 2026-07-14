let statusChart, queueChart;

document.addEventListener('DOMContentLoaded', () => {
    initCharts();
    fetchStats();
    // Poll every 2 seconds
    setInterval(fetchStats, 2000);
});

function initCharts() {
    Chart.defaults.color = '#94a3b8';
    Chart.defaults.font.family = "'Inter', sans-serif";

    const statusCtx = document.getElementById('statusChart').getContext('2d');
    statusChart = new Chart(statusCtx, {
        type: 'doughnut',
        data: {
            labels: ['Pending/Queued', 'Running', 'Success', 'Failed'],
            datasets: [{
                data: [0, 0, 0, 0],
                backgroundColor: ['#f59e0b', '#3b82f6', '#10b981', '#ef4444'],
                borderWidth: 0,
                cutout: '70%'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { position: 'right' } }
        }
    });

    const queueCtx = document.getElementById('queueChart').getContext('2d');
    queueChart = new Chart(queueCtx, {
        type: 'bar',
        data: {
            labels: ['Critical', 'High', 'Normal', 'Low'],
            datasets: [{
                label: 'Tasks in Redis',
                data: [0, 0, 0, 0],
                backgroundColor: '#3b82f6',
                borderRadius: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: { beginAtZero: true, grid: { color: 'rgba(255,255,255,0.05)' } },
                x: { grid: { display: false } }
            }
        }
    });
}

async function fetchStats() {
    try {
        const response = await fetch('/api/dashboard/stats');
        const data = await response.json();
        updateUI(data);
    } catch (error) {
        console.error('Failed to fetch stats:', error);
    }
}

function updateUI(data) {
    // Update Stats
    document.getElementById('totalTasks').textContent = data.totalTasks || 0;
    document.getElementById('pendingTasks').textContent = (data.pendingTasks || 0) + (data.queuedTasks || 0);
    document.getElementById('runningTasks').textContent = data.runningTasks || 0;
    document.getElementById('completedTasks').textContent = data.completedTasks || 0;
    document.getElementById('failedTasks').textContent = (data.failedTasks || 0) + ' / ' + (data.dlqTasks || 0);

    // Update Charts
    if(data.statusDistribution) {
        statusChart.data.datasets[0].data = [
            (data.statusDistribution.PENDING || 0) + (data.statusDistribution.QUEUED || 0),
            data.statusDistribution.RUNNING || 0,
            data.statusDistribution.SUCCESS || 0,
            data.statusDistribution.FAILED || 0
        ];
        statusChart.update();
    }

    if(data.queueSizes) {
        queueChart.data.datasets[0].data = [
            data.queueSizes.CRITICAL || 0,
            data.queueSizes.HIGH || 0,
            data.queueSizes.NORMAL || 0,
            data.queueSizes.LOW || 0
        ];
        queueChart.update();
    }

    // Update Workers
    const workersGrid = document.getElementById('workersGrid');
    workersGrid.innerHTML = '';
    let active = 0;
    if(data.workers) {
        data.workers.forEach(w => {
            if (w.status !== 'OFFLINE') active++;
            const div = document.createElement('div');
            div.className = `worker-card ${w.status.toLowerCase()}`;
            div.innerHTML = `
                <div>
                    <strong>${w.workerId}</strong>
                    <div style="font-size: 0.75rem; color: #94a3b8; margin-top: 4px;">Status: ${w.status}</div>
                </div>
            `;
            workersGrid.appendChild(div);
        });
    }
    document.getElementById('activeWorkersCount').textContent = active;

    // Update Recent Tasks
    const tbody = document.getElementById('recentTasksBody');
    tbody.innerHTML = '';
    if(data.recentTasks) {
        data.recentTasks.forEach(t => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td style="font-family: monospace; font-size: 0.85rem;">${t.id.substring(0,8)}...</td>
                <td>${t.taskName}</td>
                <td>${t.taskType}</td>
                <td><span class="status-badge ${t.status}">${t.status}</span></td>
                <td>${t.priority}</td>
                <td>${new Date(t.createdAt).toLocaleTimeString()}</td>
            `;
            tbody.appendChild(tr);
        });
    }
}
